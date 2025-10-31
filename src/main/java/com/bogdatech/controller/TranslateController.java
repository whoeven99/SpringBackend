package com.bogdatech.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bogdatech.Service.ITranslateTasksService;
import com.bogdatech.Service.ITranslatesService;
import com.bogdatech.Service.IUserTypeTokenService;
import com.bogdatech.Service.IUsersService;
import com.bogdatech.entity.DO.TranslateTasksDO;
import com.bogdatech.entity.DO.TranslatesDO;
import com.bogdatech.entity.DO.UsersDO;
import com.bogdatech.entity.VO.ImageTranslateVO;
import com.bogdatech.entity.VO.SingleTranslateVO;
import com.bogdatech.entity.VO.TranslateArrayVO;
import com.bogdatech.entity.VO.TranslatingStopVO;
import com.bogdatech.logic.TranslateService;
import com.bogdatech.logic.UserTypeTokenService;
import com.bogdatech.logic.redis.TranslationParametersRedisService;
import com.bogdatech.logic.translate.TranslateProgressService;
import com.bogdatech.model.controller.request.*;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.model.controller.response.ProgressResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static com.bogdatech.enums.ErrorEnum.*;
import static com.bogdatech.integration.ShopifyHttpIntegration.registerTransaction;
import static com.bogdatech.logic.redis.TranslationParametersRedisService.generateProgressTranslationKey;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.TypeConversionUtils.*;

@RestController
@RequestMapping("/translate")
public class TranslateController {
    @Autowired
    private TranslateService translateService;
    @Autowired
    private ITranslatesService translatesService;
    @Autowired
    private IUserTypeTokenService userTypeTokenService;
    @Autowired
    private UserTypeTokenService userTypeTokensService;
    @Autowired
    private ITranslateTasksService iTranslateTasksService;
    @Autowired
    private TranslationParametersRedisService translationParametersRedisService;
    @Autowired
    private IUsersService iUsersService;
    @Autowired
    private TranslateProgressService translateProgressService;

    // 创建手动翻译任务
    @PutMapping("/clickTranslation")
        public BaseResponse<Object> clickTranslation(@RequestParam String shopName, @RequestBody ClickTranslateRequest request) {
        request.setShopName(shopName);
        return translateService.createInitialTask(request);
    }

    @PostMapping("/getAllProgressData")
    public BaseResponse<ProgressResponse> getAllProgressData(@RequestParam String shopName, @RequestParam String source) {
        return translateProgressService.getAllProgressData(shopName, source);
    }

    /**
     * 插入shop翻译项信息
     */
    @PostMapping("/insertShopTranslateInfo")
    public void insertShopTranslateInfo(@RequestBody TranslateRequest request) {
        translatesService.insertLanguageStatus(request);
    }

    /**
     * 调用谷歌翻译的API接口
     */
    @PostMapping("/googleTranslate")
    public String googleTranslate(@RequestBody TranslateRequest request) {
        return translateService.googleTranslate(request);
    }

    /**
     * 读取所有的翻译状态信息
     */
    @GetMapping("/readTranslateInfo")
    public BaseResponse<Object> readTranslateInfo(Integer status) {
        List<TranslatesDO> list = translatesService.readTranslateInfo(status);
        if (list != null && !list.isEmpty()) {
            return new BaseResponse<>().CreateSuccessResponse(list);
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_SELECT_ERROR);
    }

    /**
     * 根据传入的数组获取对应的数据
     */
    // TODO delete
    @PostMapping("/readTranslateDOByArray")
    public BaseResponse<Object> readTranslateDOByArray(@RequestBody TranslatesDO[] translatesDOS) {
        if (translatesDOS != null && translatesDOS.length > 0) {
            TranslateArrayVO translateArrayVO = new TranslateArrayVO();
            TranslatesDO[] translatesDOResult = new TranslatesDO[translatesDOS.length];
            int i = 0;
            // 初始化 flag用于判断前端是否要继续请求
            boolean flag = false;
            for (TranslatesDO translatesDO : translatesDOS
            ) {
                translatesDOResult[i] = translatesService.readTranslateDOByArray(translatesDO);

                // 获取模块类型数据， 然后存到translatesDOResult[i]里面
                Map<Object, Object> progressTranslationKey = translationParametersRedisService.getProgressTranslationKey(generateProgressTranslationKey(translatesDOResult[i].getShopName(), translatesDOResult[i].getSource(), translatesDOResult[i].getTarget()));
                Object translatingModule = progressTranslationKey.get("translating_module");
                if (translatingModule != null) {
                    translatesDOResult[i].setResourceType((String) translatingModule);
                }

                i++;
            }

            // 判断任务表里面是否存在该任务，存在将flag改为true
            List<TranslateTasksDO> list = iTranslateTasksService.list(new LambdaQueryWrapper<TranslateTasksDO>().eq(TranslateTasksDO::getShopName, translatesDOS[0].getShopName()).in(TranslateTasksDO::getStatus, 0, 2));
            if (!list.isEmpty()) {
                flag = true;
            }
            translateArrayVO.setTranslatesDOResult(translatesDOResult);
            translateArrayVO.setFlag(flag);
            appInsights.trackTrace("readTranslateDOByArray : " + Arrays.toString(translatesDOS));
            return new BaseResponse<>().CreateSuccessResponse(translateArrayVO);
        } else {
            return new BaseResponse<>().CreateErrorResponse(DATA_IS_EMPTY);
        }
    }

    /**
     * 读取shopName的所有翻译状态信息
     */
    @GetMapping("/readInfoByShopName")
    public BaseResponse<Object> readInfoByShopName(String shopName, String source) {
        List<TranslatesDO> translatesDos = translatesService.readInfoByShopName(shopName, source);
        if (!translatesDos.isEmpty()) {
            return new BaseResponse<>().CreateSuccessResponse(translatesDos);
        }
        return new BaseResponse<>().CreateErrorResponse(SQL_SELECT_ERROR);
    }

    //暂停翻译
    @DeleteMapping("/stop")
    public void stop(@RequestParam String shopName) {
        translateService.stopTranslationManually(shopName);
    }


    //翻译单个文本数据
    @PostMapping("/translateSingleText")
    public BaseResponse<Object> translateSingleText(@RequestBody RegisterTransactionRequest request) {
        return new BaseResponse<>().CreateSuccessResponse(translateService.translateSingleText(request));
    }

    //手动停止用户的翻译任务
    @PutMapping("/stopTranslation")
    public String stopTranslation(@RequestBody TranslateRequest request) {
        return translateService.stopTranslationManually(request.getShopName());
    }

    /**
     * 将一条数据存shopify本地
     */
    @PostMapping("/insertTranslatedText")
    public void insertTranslatedText(@RequestBody CloudInsertRequest cloudServiceRequest) {
        ShopifyRequest request = new ShopifyRequest();
        request.setShopName(cloudServiceRequest.getShopName());
        request.setAccessToken(cloudServiceRequest.getAccessToken());
        request.setTarget(cloudServiceRequest.getTarget());
        Map<String, Object> body = cloudServiceRequest.getBody();
        String s = registerTransaction(request, body);
        appInsights.trackTrace("insertTranslatedText 用户 ： " + cloudServiceRequest.getShopName() + " insertTranslatedText : " + s);
    }


    //删除翻译状态的语言
    @PostMapping("/deleteFromTranslates")
    public BaseResponse<Object> deleteFromTranslates(@RequestBody TranslateRequest request) {
        Boolean b = translatesService.deleteFromTranslates(request);
        appInsights.trackTrace("deleteFromTranslates 用户删除翻译语言： " + request);
        if (b) {
            return new BaseResponse<>().CreateSuccessResponse(200);
        } else {
            return new BaseResponse<>().CreateErrorResponse(SQL_DELETE_ERROR);
        }
    }

    //将target以集合的形式插入到数据库中
    @PostMapping("/insertTargets")
    public void insertTargets(@RequestBody TargetListRequest request) {
        List<String> targetList = request.getTargetList();
        TranslateRequest translateRequest = TargetListRequestToTranslateRequest(request);
        if (!targetList.isEmpty()) {
            translateRequest.setTarget(targetList.get(0));
            userTypeTokensService.getUserInitToken(translateRequest);
            for (String target : targetList
            ) {
                TranslateRequest request1 = new TranslateRequest(0, request.getShopName(), request.getAccessToken(), request.getSource(), target, null);
                //插入语言状态
                translatesService.insertLanguageStatus(request1);
                //获取translates表中shopName和target对应的id
                int idByShopNameAndTarget = translateService.getIdByShopNameAndTargetAndSource(request1.getShopName(), request1.getTarget(), request1.getSource());
                //初始化用户对应token表
                userTypeTokenService.insertTypeInfo(request1, idByShopNameAndTarget);
            }
        } else {
            translateRequest.setTarget("asdf");
            userTypeTokensService.getUserInitToken(translateRequest);
        }
    }

    //当支付成功后，调用该方法，将该用户的状态3，改为状态6
    @PostMapping("/updateStatus")
    public void updateStatus3To6(@RequestBody TranslateRequest request) {
        translatesService.updateStatus3To6(request.getShopName());
    }

    //用户是否选择定时任务的方法
    @PostMapping("/updateAutoTranslateByData")
    public BaseResponse<Object> updateStatusByShopName(@RequestBody AutoTranslateRequest request) {
        // 判断用户的语言是否在数据库中，在不做操作，不在，进行同步
        TranslatesDO one = translatesService.getOne(new QueryWrapper<TranslatesDO>().eq("shop_name", request.getShopName()).eq("source", request.getSource()).eq("target", request.getTarget()));

        // 获取用户token
        UsersDO usersDO = iUsersService.getUserByName(request.getShopName());
        if (one == null) {
            //走同步逻辑
            translateService.syncShopifyAndDatabase(new TranslateRequest(0, request.getShopName(), usersDO.getAccessToken(), request.getSource(), request.getTarget(), null));
        }
        return translatesService.updateAutoTranslateByShopName(request.getShopName(), request.getAutoTranslate(), request.getSource(), request.getTarget());
    }

    //单条文本翻译
    @PostMapping("/singleTextTranslate")
    public BaseResponse<Object> singleTextTranslate(@RequestBody SingleTranslateVO singleTranslateVO) {
        return translateService.singleTextTranslate(singleTranslateVO);
    }

    /**
     * 停止翻译按钮
     * 将所有状态0和状态2的任务改成7
     */
    @PutMapping("/stopTranslatingTask")
    public BaseResponse<Object> stopTranslatingTask(@RequestParam String shopName, @RequestBody TranslatingStopVO translatingStopVO) {
        Boolean stopFlag = translationParametersRedisService.setStopTranslationKey(shopName);
        if (!stopFlag) {
           return new BaseResponse<>().CreateErrorResponse("already stopped");
        }

        // 将所有状态2的任务改成7
        translatesService.updateStopStatus(shopName, translatingStopVO.getSource());

        // 将所有状态为0和2的task任务，改为7
        Boolean flag = iTranslateTasksService.updateStatus0And2To7(shopName);
        if (flag) {
            return new BaseResponse<>().CreateSuccessResponse(true);
        }
        return new BaseResponse<>().CreateErrorResponse(false);
    }

    /**
     * 用于获取进度条的相关数据
     */
    @PostMapping("/getProgressData")
    public BaseResponse<Object> getProgressData(@RequestParam String shopName, @RequestParam String target, @RequestParam String source) {
        Map<String, Integer> progressData = translateService.getProgressData(shopName, target, source);
        appInsights.trackTrace("getProgressData " + shopName + " target : " + target + " " + source + " " + progressData);
        if (progressData != null) {
            return new BaseResponse<>().CreateSuccessResponse(progressData);
        }

        return new BaseResponse<>().CreateErrorResponse(false);
    }

    /**
     * 图片翻译
     */
    @PutMapping("/imageTranslate")
    public BaseResponse<Object> imageTranslate(@RequestParam String shopName, @RequestBody ImageTranslateVO imageTranslateVO) {
        String targetPic = translateService.imageTranslate(imageTranslateVO.getSourceCode(), imageTranslateVO.getTargetCode(), imageTranslateVO.getImageUrl(), shopName, imageTranslateVO.getAccessToken());

        if (targetPic != null) {
            return new BaseResponse<>().CreateSuccessResponse(targetPic);
        }
        return new BaseResponse<>().CreateErrorResponse(false);
    }
}
