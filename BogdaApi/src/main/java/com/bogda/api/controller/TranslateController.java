package com.bogda.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bogda.api.Service.ITranslatesService;
import com.bogda.api.Service.IUserTypeTokenService;
import com.bogda.api.Service.IUsersService;
import com.bogda.api.entity.DO.TranslatesDO;
import com.bogda.api.entity.DO.UsersDO;
import com.bogda.api.entity.VO.*;
import com.bogda.api.integration.ShopifyHttpIntegration;
import com.bogda.api.logic.TranslateService;
import com.bogda.api.logic.UserTypeTokenService;
import com.bogda.api.logic.redis.RedisStoppedRepository;
import com.bogda.api.logic.redis.TranslationParametersRedisService;
import com.bogda.api.logic.translate.TranslateV2Service;
import com.bogda.api.model.controller.request.*;
import com.bogda.api.model.controller.response.BaseResponse;
import com.bogda.api.model.controller.response.ProgressResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import static com.bogda.api.enums.ErrorEnum.*;
import static com.bogda.api.utils.CaseSensitiveUtils.appInsights;
import static com.bogda.api.utils.TypeConversionUtils.TargetListRequestToTranslateRequest;

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
    private TranslationParametersRedisService translationParametersRedisService;
    @Autowired
    private IUsersService iUsersService;
    @Autowired
    private TranslateV2Service translateV2Service;
    @Autowired
    private ShopifyHttpIntegration shopifyHttpIntegration;

    // 创建手动翻译任务
    @PutMapping("/clickTranslation")
    public BaseResponse<Object> clickTranslation(@RequestParam String shopName, @RequestBody ClickTranslateRequest request) {
        request.setShopName(shopName);
        return translateV2Service.createInitialTask(request);
    }

    @PostMapping("/getAllProgressData")
    public BaseResponse<ProgressResponse> getAllProgressData(@RequestParam String shopName, @RequestParam String source) {
        return translateV2Service.getProcess(shopName, source);
    }

    // 单条文本翻译 修改返回值类型
    @PostMapping("/singleTextTranslateV2")
    public BaseResponse<SingleReturnVO> singleTextTranslateV2(@RequestParam String shopName, @RequestBody SingleTranslateVO singleTranslateVO) {
        singleTranslateVO.setShopName(shopName);
        return translateV2Service.singleTextTranslate(singleTranslateVO);
    }

    // 用户手动点击停止翻译
    @PutMapping("/stopTranslatingTask")
    public BaseResponse<Object> stopTranslatingTask(@RequestParam String shopName, @RequestBody TranslatingStopVO translatingStopVO) {
        redisStoppedRepository.manuallyStopped(shopName);

        // 目前能用到的就是私有key
        Future<?> future = TranslateService.userTasks.get(shopName);
        if (future != null && !future.isDone()) {
            // 中断正在执行的任务
            future.cancel(true);
        }
        return new BaseResponse<>().CreateSuccessResponse(true);
//        Boolean stopFlag = translationParametersRedisService.setStopTranslationKey(shopName);
//        if (!stopFlag) {
//            return new BaseResponse<>().CreateErrorResponse("already stopped");
//        }
//
//        // 将所有状态2的任务改成7
//        translatesService.updateStopStatus(shopName, translatingStopVO.getSource());
//
//        // 将所有状态为0和2的task任务，改为7
//        Boolean flag = iTranslateTasksService.updateStatus0And2To7(shopName);
//        if (flag) {
//            return new BaseResponse<>().CreateSuccessResponse(true);
//        }
//        return new BaseResponse<>().CreateErrorResponse(false);
    }

    // 用户手动点击继续翻译
    @PostMapping("/continueTranslating")
    public BaseResponse<Object> continueTranslating(@RequestParam String shopName, @RequestParam Integer taskId) {
        return translateV2Service.continueTranslating(shopName, taskId);
    }

    // 当支付成功后，调用该方法，将该用户的状态3，改为状态6
    // 支付之后，前端调用api，停止状态改为继续翻译
    @PostMapping("/updateStatus")
    public BaseResponse<Object> updateStatus3To6(@RequestBody TranslateRequest request) {
        if (translatesService.updateStatus3To6(request.getShopName())) {
            translateV2Service.continueTranslating(request.getShopName());
            return new BaseResponse<>().CreateSuccessResponse(true);
        } else {
            return new BaseResponse<>().CreateErrorResponse("updateStatus3To6 error");
        }
    }

    /**
     * 插入shop翻译项信息
     */
    @PostMapping("/insertShopTranslateInfo")
    public void insertShopTranslateInfo(@RequestBody TranslateRequest request) {
        translatesService.insertLanguageStatus(request);
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
    @PostMapping("/readTranslateDOByArray")
    public BaseResponse<Object> readTranslateDOByArray(@RequestBody TranslatesDO[] translatesDOS) {
        if (translatesDOS != null && translatesDOS.length > 0) {
            TranslateArrayVO translateArrayVO = new TranslateArrayVO();
            TranslatesDO[] translatesDOResult = new TranslatesDO[translatesDOS.length];
            int i = 0;

            // 初始化 flag用于判断前端是否要继续请求
            for (TranslatesDO translatesDO : translatesDOS
            ) {
                translatesDOResult[i] = translatesService.readTranslateDOByArray(translatesDO);

                // 获取模块类型数据， 然后存到translatesDOResult[i]里面
                Map<String, String> progressTranslationKey = translationParametersRedisService.getProgressTranslationKey(TranslationParametersRedisService.generateProgressTranslationKey(translatesDOResult[i].getShopName(), translatesDOResult[i].getSource(), translatesDOResult[i].getTarget()));
                String translatingModule = progressTranslationKey.get("translating_module");
                if (translatingModule != null) {
                    translatesDOResult[i].setResourceType(translatingModule);
                }

                i++;
            }

            translateArrayVO.setTranslatesDOResult(translatesDOResult);
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

    @Autowired
    private RedisStoppedRepository redisStoppedRepository;

    // 恢复翻译任务
    @PostMapping("/revertStop")
    public void continueTranslation(@RequestBody TranslateRequest request) {
        String shopName = request.getShopName();
        redisStoppedRepository.removeStoppedFlag(shopName);
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
        String s = shopifyHttpIntegration.registerTransaction(request, body);
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

    //用户是否选择定时任务的方法
    @PostMapping("/updateAutoTranslateByData")
    public BaseResponse<Object> updateStatusByShopName(@RequestBody AutoTranslateRequest request) {
        // 判断用户的语言是否在数据库中，在不做操作，不在，进行同步
        TranslatesDO one = translatesService.getOne(new QueryWrapper<TranslatesDO>().eq("shop_name", request.getShopName()).eq("source", request.getSource()).eq("target", request.getTarget()));

        // 获取用户token
        UsersDO usersDO = iUsersService.getUserByName(request.getShopName());
        if (one == null) {
            //走同步逻辑
            translateService.syncShopifyAndDatabase(request.getShopName(), usersDO.getAccessToken(), request.getSource());
        }
        return translatesService.updateAutoTranslateByShopName(request.getShopName(), request.getAutoTranslate(), request.getSource(), request.getTarget());
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
