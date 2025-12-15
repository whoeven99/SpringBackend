package com.bogdatech.logic.PCApp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bogdatech.constants.TranslateConstants;
import com.bogdatech.entity.DO.PCUserPicturesDO;
import com.bogdatech.entity.DO.PCUsersDO;
import com.bogdatech.entity.VO.AltTranslateVO;
import com.bogdatech.entity.VO.ImageTranslateVO;
import com.bogdatech.integration.ALiYunTranslateIntegration;
import com.bogdatech.integration.AidgeIntegration;
import com.bogdatech.integration.HunYuanBucketIntegration;
import com.bogdatech.integration.HuoShanIntegration;
import com.bogdatech.logic.token.UserTokenService;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.repository.repo.PCUserPicturesRepo;
import com.bogdatech.repository.repo.PCUsersRepo;
import com.bogdatech.utils.PictureUtils;
import com.bogdatech.utils.StringUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static com.bogdatech.controller.UserPicturesController.allowedMimeTypes;
import static com.bogdatech.logic.TranslateService.OBJECT_MAPPER;
import static com.bogdatech.utils.ApiCodeUtils.getLanguageName;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

@Component
public class PCUserPicturesService {
    @Autowired
    private PCUserPicturesRepo pcUserPicturesRepo;
    @Autowired
    private PCUsersRepo pcUsersRepo;
    @Autowired
    private ALiYunTranslateIntegration aLiYunTranslateIntegration;
    @Autowired
    private AidgeIntegration aidgeIntegration;
    @Autowired
    private HuoShanIntegration huoShanIntegration;
    @Autowired
    private UserTokenService userTokenService;

    public static String CDN_URL = "https://img.bogdatech.com";
    public static String COS_URL = "https://ciwi-us-1327177217.cos.na-ashburn.myqcloud.com";
    public static int APP_PIC_FEE = 2000;
    public static int APP_ALT_FEE = 2000; // alt和pic翻译一块扣除

    public BaseResponse<Object> insertPicToDbAndCloud(MultipartFile file, String shopName, String pcUserPicturesDoJson) {
        //解析userPicturesDO
        PCUserPicturesDO pcUserPicturesDO = null;
        try {
            pcUserPicturesDO = OBJECT_MAPPER.readValue(pcUserPicturesDoJson, PCUserPicturesDO.class);
        } catch (JsonProcessingException e) {
            appInsights.trackTrace("insertPictureToDbAndCloud " + shopName + " userPicturesDoJson 解析失败 errors " + e);
        }

        // 先判断是否有图片,有图片做上传和插入更新数据;没有图片,做插入和更新数据
        if (!file.isEmpty() && pcUserPicturesDO != null && pcUserPicturesDO.getImageId() != null) {
            // 做图片的限制
            if (!allowedMimeTypes.contains(file.getContentType())) {
                return new BaseResponse<>().CreateErrorResponse("Image format error");
            }

            // 将图片上传到腾讯云
            String afterUrl = HunYuanBucketIntegration.uploadFile(file, shopName, pcUserPicturesDO.getImageId());
            pcUserPicturesDO.setImageAfterUrl(afterUrl);

            // 再将图片相关数据存到数据库中
            pcUserPicturesDO.setShopName(shopName);
            boolean b = pcUserPicturesRepo.insertPictureData(pcUserPicturesDO);

            // 数据库做上传和插入更新数据
            if (afterUrl != null && b) {
                return new BaseResponse<>().CreateSuccessResponse(pcUserPicturesDO);
            } else {
                return new BaseResponse<>().CreateErrorResponse(false);
            }
        } else if (file.isEmpty() && pcUserPicturesDO != null && pcUserPicturesDO.getImageId() != null) {
            boolean b = pcUserPicturesRepo.insertPictureData(pcUserPicturesDO);
            if (b) {
                return new BaseResponse<>().CreateSuccessResponse(pcUserPicturesDO);
            } else {
                return new BaseResponse<>().CreateErrorResponse(false);
            }
        }
        return new BaseResponse<>().CreateErrorResponse(false);
    }

    public BaseResponse<Object> deletePicByShopNameAndPCUserPictures(String shopName, PCUserPicturesDO pcUserPicturesDO) {
        boolean flag = pcUserPicturesRepo.deletePictureData(shopName, pcUserPicturesDO.getImageId(), pcUserPicturesDO.getImageBeforeUrl(), pcUserPicturesDO.getLanguageCode());
        if (flag) {
            pcUserPicturesDO.setIsDeleted(1);
            return new BaseResponse<>().CreateSuccessResponse(pcUserPicturesDO);
        }
        return new BaseResponse<>().CreateErrorResponse(false);
    }

    public BaseResponse<Object> translatePic(String shopName, ImageTranslateVO imageTranslateVO) {
        appInsights.trackTrace("imageTranslate 用户 " + shopName + " sourceCode " + imageTranslateVO.getSourceCode() + " targetCode " + imageTranslateVO.getTargetCode() + " imageUrl " + imageTranslateVO.getImageUrl() + " accessToken " + imageTranslateVO.getAccessToken());

        // 判断 图片格式，语言范围，然后选择模型翻译
        String imageUrl = imageTranslateVO.getImageUrl();
        int modelType = imageTranslateVO.getModelType();
        String sourceCode = imageTranslateVO.getSourceCode();
        String targetCode = imageTranslateVO.getTargetCode();
        String extensionFromUrl = PictureUtils.getExtensionFromUrl(imageUrl);

        // 特殊语言 繁体中文  zh-tw 当modelType为2时要改为 zh-Hant sourceCode 或 targetCode都要改
        if (modelType == 2 && ("zh-tw".equals(sourceCode) || "zh-tw".equals(targetCode))) {
            if ("zh-tw".equals(sourceCode)) {
                sourceCode = "zh-Hant";
            }
            if ("zh-tw".equals(targetCode)) {
                targetCode = "zh-Hant";
            }
        }

        if (extensionFromUrl == null) {
            return new BaseResponse<>().CreateErrorResponse("The image format is incorrect.");
        }

        // 判断后缀是否符合模型要求  huoShan 只支持 png和jpg， aidge支持png、jpeg、jpg、bmp、webp
        boolean allowedExtension = PictureUtils.isSupportModelAndImageType(extensionFromUrl, modelType);
        if (!allowedExtension) {
            return new BaseResponse<>().CreateErrorResponse("The image format is not supported by the model.");
        }

        // 判断传入的modelType 和 sourceCode、targetCode 是否符合要求
        boolean differentImageTranslateInputCode = PictureUtils.isDifferentImageTranslateInputCode(sourceCode, targetCode, modelType);
        if (!differentImageTranslateInputCode) {
            return new BaseResponse<>().CreateErrorResponse("The source language and target language are not supporting.");
        }

        // 获取用户token，判断是否和数据库中一致再选择是否调用
        PCUsersDO pcUsersDO = pcUsersRepo.getUserByShopName(shopName);
        if (!pcUsersDO.getAccessToken().equals(imageTranslateVO.getAccessToken())) {
            return new BaseResponse<>().CreateErrorResponse("accessToken error");
        }

        // 获取用户最大额度限制
        Integer maxCharsByShopName = pcUsersDO.getPurchasePoints();

        // 剩余额度
        int remainingPoints = pcUsersDO.getPurchasePoints() - pcUsersDO.getUsedPoints();
        if (pcUsersDO.getUsedPoints() >= maxCharsByShopName || remainingPoints < APP_PIC_FEE) {
            return new BaseResponse<>().CreateErrorResponse("Insufficient credit");
        }

        // 根据modelType 选择不同模型翻译
        String targetPic = null;
        if (modelType == 1) {
            targetPic = aidgeIntegration.aidgeStandPictureTranslate(shopName, imageTranslateVO.getImageUrl()
                    , imageTranslateVO.getSourceCode(), imageTranslateVO.getTargetCode(), maxCharsByShopName, AidgeIntegration.PICTURE_APP);
        } else if (modelType == 2) {
            targetPic = huoShanImageTranslate(imageUrl, shopName, AidgeIntegration.PICTURE_APP, targetCode, maxCharsByShopName);
        }

        if (targetPic != null) {
            return new BaseResponse<>().CreateSuccessResponse(targetPic);
        }
        return new BaseResponse<>().CreateErrorResponse(false);
    }

    public BaseResponse<Object> getPicsByImageIdAndShopName(String shopName, PCUserPicturesDO pcUserPicturesDO) {
        List<PCUserPicturesDO> pcUserPicturesDOS = pcUserPicturesRepo.listPcUserPics(shopName, pcUserPicturesDO.getImageId());

        if (pcUserPicturesDOS != null) {
            return new BaseResponse<>().CreateSuccessResponse(pcUserPicturesDOS);
        }
        return new BaseResponse<>().CreateErrorResponse(false);
    }

    public BaseResponse<Object> altTranslate(String shopName, AltTranslateVO altTranslateVO) {
        appInsights.trackTrace("altTranslate 用户 " + shopName + " sourceCode " + altTranslateVO.getTargetCode() + " targetCode " + altTranslateVO.getTargetCode() + " alt " + altTranslateVO.getAlt() + " accessToken " + altTranslateVO.getAccessToken());
        // 获取用户token，判断是否和数据库中一致再选择是否调用
        PCUsersDO pcUsersDO = pcUsersRepo.getUserByShopName(shopName);
        if (!pcUsersDO.getAccessToken().equals(altTranslateVO.getAccessToken())) {
            return null;
        }

        // 获取用户最大额度限制
        Integer maxCharsByShopName = pcUsersDO.getPurchasePoints();

        // 生成提示词
        String prompt = "请将以下文本翻译为如下语言：" + getLanguageName(altTranslateVO.getTargetCode());

        // 调用文本翻译方法（暂定ciwi）
        String targetText = aLiYunTranslateIntegration.textTranslate(altTranslateVO.getAlt(), prompt, altTranslateVO.getTargetCode(), shopName, maxCharsByShopName);
        if (targetText != null) {
            return new BaseResponse<>().CreateSuccessResponse(targetText);
        }
        return new BaseResponse<>().CreateErrorResponse(false);
    }

    public BaseResponse<Object> updateUserPic(String shopName, PCUserPicturesDO pcUserPicturesDO) {
        pcUserPicturesDO.setShopName(shopName);
        // 判断是否存在， 存在则更新，不存在则插入
        List<PCUserPicturesDO> userPicByShopNameAndImageIdAndLanguageCode = pcUserPicturesRepo.getUserPicByShopNameAndImageIdAndLanguageCode(shopName, pcUserPicturesDO.getImageId(), pcUserPicturesDO.getLanguageCode());
        boolean flag;
        if (userPicByShopNameAndImageIdAndLanguageCode == null || userPicByShopNameAndImageIdAndLanguageCode.isEmpty()) {
            flag = pcUserPicturesRepo.insertPictureData(pcUserPicturesDO);
        } else {
            flag = pcUserPicturesRepo.updatePictureData(shopName, pcUserPicturesDO);
        }

        if (flag) {
            return new BaseResponse<>().CreateSuccessResponse(pcUserPicturesDO);
        }
        return new BaseResponse<>().CreateErrorResponse(false);
    }

    public BaseResponse<Object> selectPictureDataByShopNameAndProductIdAndLanguageCode(String shopName, String productId, String languageCode) {
        List<PCUserPicturesDO> list = pcUserPicturesRepo.list(new LambdaQueryWrapper<PCUserPicturesDO>().eq(PCUserPicturesDO::getShopName, shopName).eq(PCUserPicturesDO::getProductId, productId).eq(PCUserPicturesDO::getLanguageCode, languageCode).eq(PCUserPicturesDO::getIsDeleted, 0));
        if (list != null) {
            // 替换图片url
            list.forEach(pic -> {
                if (pic.getImageAfterUrl() != null) {
                    pic.setImageAfterUrl(pic.getImageAfterUrl().replace(COS_URL, CDN_URL));
                }
            });
            return new BaseResponse<>().CreateSuccessResponse(list);
        }
        return new BaseResponse<>().CreateErrorResponse("null");
    }

    public BaseResponse<Object> selectPicturesByShopNameAndLanguageCode(String shopName, String languageCode) {
        List<PCUserPicturesDO> list = pcUserPicturesRepo.list(new LambdaQueryWrapper<PCUserPicturesDO>().eq(PCUserPicturesDO::getShopName, shopName).eq(PCUserPicturesDO::getLanguageCode, languageCode).eq(PCUserPicturesDO::getIsDeleted, 0));
        if (list != null) {
            list.forEach(pic -> {
                if (pic.getImageAfterUrl() != null) {
                    pic.setImageAfterUrl(pic.getImageAfterUrl().replace(COS_URL, CDN_URL));
                }
            });
            return new BaseResponse<>().CreateSuccessResponse(list);
        }
        return new BaseResponse<>().CreateErrorResponse("null");
    }

    public BaseResponse<Object> deleteTranslateUrl(String shopName, PCUserPicturesDO pcUserPicturesDO) {
        boolean flag = pcUserPicturesRepo.updatePictureAfterUrl(shopName, pcUserPicturesDO.getImageId(), pcUserPicturesDO.getImageBeforeUrl(), pcUserPicturesDO.getLanguageCode());
        if (flag) {
            return new BaseResponse<>().CreateSuccessResponse(true);
        }
        return new BaseResponse<>().CreateErrorResponse(false);
    }

    // 火山翻译图片 将翻译完的图片存bucket里，然后输出url
    public String huoShanImageTranslate(String imageUrl, String shopName, String appType, String languageCode, int limitChars) {
        // 先火山翻译
        byte[] bytes = huoShanIntegration.huoShanImageTranslate(imageUrl, languageCode);

        // 计算额度
        if (ALiYunTranslateIntegration.TRANSLATE_APP.equals(appType)) {
            userTokenService.addUsedToken(shopName, TranslateConstants.PIC_FEE);
        } else {
            pcUsersRepo.updateUsedPointsByShopName(shopName, PCUserPicturesService.APP_PIC_FEE);
        }

        // 存bucket 桶里面
        String key = HunYuanBucketIntegration.PATH_NAME + "/" + shopName + "/" + StringUtils.generate8DigitNumber() + ".jpg";

        // 将这个bytes存到bucket里
        return HunYuanBucketIntegration.uploadBytes(bytes, key, "image/jpeg");
    }
}
