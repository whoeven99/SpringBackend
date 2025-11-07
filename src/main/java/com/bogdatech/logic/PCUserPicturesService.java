package com.bogdatech.logic;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bogdatech.Service.IPCUserPicturesService;
import com.bogdatech.Service.IPCUserService;
import com.bogdatech.entity.DO.PCUserPicturesDO;
import com.bogdatech.entity.DO.PCUsersDO;
import com.bogdatech.entity.VO.AltTranslateVO;
import com.bogdatech.entity.VO.ImageTranslateVO;
import com.bogdatech.integration.ALiYunTranslateIntegration;
import com.bogdatech.model.controller.response.BaseResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static com.bogdatech.controller.UserPicturesController.allowedMimeTypes;
import static com.bogdatech.integration.ALiYunTranslateIntegration.PICTURE_APP;
import static com.bogdatech.integration.HunYuanBucketIntegration.uploadFile;
import static com.bogdatech.logic.TranslateService.OBJECT_MAPPER;
import static com.bogdatech.utils.ApiCodeUtils.getLanguageName;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

@Component
public class PCUserPicturesService {
    @Autowired
    private IPCUserPicturesService ipcUserPicturesService;
    @Autowired
    private IPCUserService ipcUserService;
    @Autowired
    private ALiYunTranslateIntegration aLiYunTranslateIntegration;

    public static String CDN_URL = "https://img.bogdatech.com";
    public static String COS_URL = "https://ciwi-us-1327177217.cos.na-ashburn.myqcloud.com";
    public static int APP_PIC_FEE = 1000;
    public static int APP_ALT_FEE = 200;

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
            //做图片的限制
            if (!allowedMimeTypes.contains(file.getContentType())) {
                return new BaseResponse<>().CreateErrorResponse("Image format error");
            }
            //将图片上传到腾讯云
            String afterUrl = uploadFile(file, shopName, pcUserPicturesDO.getImageId());
            pcUserPicturesDO.setImageAfterUrl(afterUrl);
            //再将图片相关数据存到数据库中
            pcUserPicturesDO.setShopName(shopName);
            boolean b = ipcUserPicturesService.insertPictureData(pcUserPicturesDO);
            //数据库做上传和插入更新数据
            if (afterUrl != null && b) {
                return new BaseResponse<>().CreateSuccessResponse(pcUserPicturesDO);
            } else {
                return new BaseResponse<>().CreateErrorResponse(false);
            }
        } else if (file.isEmpty() && pcUserPicturesDO != null && pcUserPicturesDO.getImageId() != null) {
            boolean b = ipcUserPicturesService.insertPictureData(pcUserPicturesDO);
            if (b) {
                return new BaseResponse<>().CreateSuccessResponse(pcUserPicturesDO);
            } else {
                return new BaseResponse<>().CreateErrorResponse(false);
            }
        }
        return new BaseResponse<>().CreateErrorResponse(false);
    }

    public BaseResponse<Object> deletePicByShopNameAndPCUserPictures(String shopName, PCUserPicturesDO pcUserPicturesDO) {
        boolean flag = ipcUserPicturesService.deletePictureData(shopName, pcUserPicturesDO.getImageId(), pcUserPicturesDO.getImageBeforeUrl(), pcUserPicturesDO.getLanguageCode());
        if (flag) {
            pcUserPicturesDO.setIsDeleted(1);
            return new BaseResponse<>().CreateSuccessResponse(pcUserPicturesDO);
        }
        return new BaseResponse<>().CreateErrorResponse(false);
    }

    public BaseResponse<Object> translatePic(String shopName, ImageTranslateVO imageTranslateVO) {
        appInsights.trackTrace("imageTranslate 用户 " + shopName + " sourceCode " + imageTranslateVO.getSourceCode() + " targetCode " + imageTranslateVO.getTargetCode() + " imageUrl " + imageTranslateVO.getImageUrl() + " accessToken " + imageTranslateVO.getAccessToken());

        // 获取用户token，判断是否和数据库中一致再选择是否调用
        PCUsersDO pcUsersDO = ipcUserService.getUserByShopName(shopName);
        if (!pcUsersDO.getAccessToken().equals(imageTranslateVO.getAccessToken())) {
            return null;
        }

        // 获取用户最大额度限制
        Integer maxCharsByShopName = pcUsersDO.getPurchasePoints();

        // 剩余额度
        int remainingPoints = pcUsersDO.getPurchasePoints() - pcUsersDO.getUsedPoints();
        if (pcUsersDO.getUsedPoints() >= maxCharsByShopName || remainingPoints < APP_PIC_FEE) {
            return new BaseResponse<>().CreateErrorResponse("额度不够");
        }

        // 调用图片翻译方法
        String targetPic = aLiYunTranslateIntegration.callWithPic(imageTranslateVO.getSourceCode(), imageTranslateVO.getTargetCode(), imageTranslateVO.getImageUrl(), shopName, maxCharsByShopName, PICTURE_APP);

        if (targetPic != null) {
            return new BaseResponse<>().CreateSuccessResponse(targetPic);
        }
        return new BaseResponse<>().CreateErrorResponse(false);
    }

    public BaseResponse<Object> getPicsByImageIdAndShopName(String shopName, PCUserPicturesDO pcUserPicturesDO) {
        List<PCUserPicturesDO> pcUserPicturesDOS = ipcUserPicturesService.listPcUserPics(shopName, pcUserPicturesDO.getImageId());

        if (pcUserPicturesDOS != null) {
            return new BaseResponse<>().CreateSuccessResponse(pcUserPicturesDOS);
        }
        return new BaseResponse<>().CreateErrorResponse(false);
    }

    public BaseResponse<Object> altTranslate(String shopName, AltTranslateVO altTranslateVO) {
        appInsights.trackTrace("altTranslate 用户 " + shopName + " sourceCode " + altTranslateVO.getTargetCode() + " targetCode " + altTranslateVO.getTargetCode() + " alt " + altTranslateVO.getAlt() + " accessToken " + altTranslateVO.getAccessToken());
        // 获取用户token，判断是否和数据库中一致再选择是否调用
        PCUsersDO pcUsersDO = ipcUserService.getUserByShopName(shopName);
        if (!pcUsersDO.getAccessToken().equals(altTranslateVO.getAccessToken())) {
            return null;
        }

        // 获取用户最大额度限制
        Integer maxCharsByShopName = pcUsersDO.getPurchasePoints();

        // 剩余额度
        int remainingPoints = pcUsersDO.getPurchasePoints() - pcUsersDO.getUsedPoints();
        if (pcUsersDO.getUsedPoints() >= maxCharsByShopName || remainingPoints < APP_ALT_FEE) {
            return new BaseResponse<>().CreateErrorResponse("额度不够");
        }

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
        List<PCUserPicturesDO> userPicByShopNameAndImageIdAndLanguageCode = ipcUserPicturesService.getUserPicByShopNameAndImageIdAndLanguageCode(shopName, pcUserPicturesDO.getImageId(), pcUserPicturesDO.getLanguageCode());
        boolean flag = false;
        if (userPicByShopNameAndImageIdAndLanguageCode == null || userPicByShopNameAndImageIdAndLanguageCode.isEmpty()) {
            flag = ipcUserPicturesService.insertPictureData(pcUserPicturesDO);
        } else {
            flag = ipcUserPicturesService.updatePictureData(shopName, pcUserPicturesDO);
        }

        if (flag) {
            return new BaseResponse<>().CreateSuccessResponse(pcUserPicturesDO);
        }
        return new BaseResponse<>().CreateErrorResponse(false);
    }

    public BaseResponse<Object> selectPictureDataByShopNameAndProductIdAndLanguageCode(String shopName, String productId, String languageCode) {
        List<PCUserPicturesDO> list = ipcUserPicturesService.list(new LambdaQueryWrapper<PCUserPicturesDO>().eq(PCUserPicturesDO::getShopName, shopName).eq(PCUserPicturesDO::getProductId, productId).eq(PCUserPicturesDO::getLanguageCode, languageCode).eq(PCUserPicturesDO::getIsDeleted, 0));
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
        List<PCUserPicturesDO> list = ipcUserPicturesService.list(new LambdaQueryWrapper<PCUserPicturesDO>().eq(PCUserPicturesDO::getShopName, shopName).eq(PCUserPicturesDO::getLanguageCode, languageCode).eq(PCUserPicturesDO::getIsDeleted, 0));
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
        boolean flag = ipcUserPicturesService.updatePictureAfterUrl(shopName, pcUserPicturesDO.getImageId(), pcUserPicturesDO.getImageBeforeUrl(), pcUserPicturesDO.getLanguageCode());
        if (flag) {
            return new BaseResponse<>().CreateSuccessResponse(true);
        }
        return new BaseResponse<>().CreateErrorResponse(false);
    }
}
