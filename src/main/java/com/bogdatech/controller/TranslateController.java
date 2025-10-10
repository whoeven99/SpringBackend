package com.bogdatech.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bogdatech.Service.ITranslateTasksService;
import com.bogdatech.Service.ITranslatesService;
import com.bogdatech.Service.ITranslationCounterService;
import com.bogdatech.Service.IUserTypeTokenService;
import com.bogdatech.entity.DO.TranslateTasksDO;
import com.bogdatech.entity.DO.TranslatesDO;
import com.bogdatech.entity.DO.TranslationCounterDO;
import com.bogdatech.entity.VO.ImageTranslateVO;
import com.bogdatech.entity.VO.SingleTranslateVO;
import com.bogdatech.entity.VO.TranslateArrayVO;
import com.bogdatech.entity.VO.TranslatingStopVO;
import com.bogdatech.logic.RabbitMqTranslateService;
import com.bogdatech.logic.RedisProcessService;
import com.bogdatech.logic.TranslateService;
import com.bogdatech.logic.UserTypeTokenService;
import com.bogdatech.model.controller.request.*;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import static com.bogdatech.constants.TranslateConstants.HAS_TRANSLATED;
import static com.bogdatech.enums.ErrorEnum.*;
import static com.bogdatech.integration.ShopifyHttpIntegration.registerTransaction;
import static com.bogdatech.logic.TranslateService.*;
import static com.bogdatech.logic.TranslateService.userStopFlags;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.MapUtils.getTranslationStatusMap;
import static com.bogdatech.utils.ModelUtils.translateModel;
import static com.bogdatech.utils.RedisKeyUtils.generateProcessKey;
import static com.bogdatech.utils.TypeConversionUtils.*;

@RestController
@RequestMapping("/translate")
public class TranslateController {
    @Autowired
    private TranslateService translateService;
    @Autowired
    private ITranslatesService translatesService;
    @Autowired
    private ITranslationCounterService translationCounterService;
    @Autowired
    private IUserTypeTokenService userTypeTokenService;
    @Autowired
    private UserTypeTokenService userTypeTokensService;
    @Autowired
    private RabbitMqTranslateService rabbitMqTranslateService;
    @Autowired
    private ITranslateTasksService iTranslateTasksService;
    @Autowired
    private RedisProcessService redisProcessService;


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
    @PostMapping("/readTranslateDOByArray")
    public BaseResponse<Object> readTranslateDOByArray(@RequestBody TranslatesDO[] translatesDOS) {
        if (translatesDOS != null && translatesDOS.length > 0) {
            TranslateArrayVO translateArrayVO = new TranslateArrayVO();
            TranslatesDO[] translatesDOResult = new TranslatesDO[translatesDOS.length];
            int i = 0;
            //初始化 flag用于判断前端是否要继续请求
            boolean flag = false;
            for (TranslatesDO translatesDO : translatesDOS
            ) {
                translatesDOResult[i] = translatesService.readTranslateDOByArray(translatesDO);
                i++;
            }
            //判断任务表里面是否存在该任务，存在将flag改为true
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
     * 根据传入的shopName和source，返回一个最新时间的翻译项数据，status为1-3
     */
    @PostMapping("/getTranslateDOByShopNameAndSource")
    public BaseResponse<Object> getTranslateDOByShopNameAndSource(@RequestBody TranslateRequest request) {
        if (request != null) {
            //改为返回状态为2的所有相关数据
            List<TranslatesDO> list = translatesService.list(new LambdaQueryWrapper<TranslatesDO>().eq(TranslatesDO::getShopName, request.getShopName()).eq(TranslatesDO::getSource, request.getSource()).eq(TranslatesDO::getStatus, 2).orderByDesc(TranslatesDO::getUpdateAt));
            if (list.isEmpty()) {
                TranslatesDO translatesDO = translatesService.selectLatestOne(request);
                translatesDO.setAccessToken(null);
                list.add(translatesDO);
                return new BaseResponse<>().CreateSuccessResponse(list);
            }
            return new BaseResponse<>().CreateSuccessResponse(list);
        }
        return new BaseResponse<>().CreateErrorResponse(DATA_IS_EMPTY);
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

    /**
     * 通过TranslateResourceDTO获取定义好的数组，对其进行for循环，遍历获得query，通过发送shopify的API获得数据，获得数据后再通过百度翻译API翻译数据
     */
    @PutMapping("/clickTranslation")
    public BaseResponse<Object> clickTranslation(@RequestParam String shopName, @RequestBody ClickTranslateRequest clickTranslateRequest) {
        appInsights.trackTrace("clickTranslation : " + clickTranslateRequest);
        // 判断前端传的数据是否完整，如果不完整，报错
        if (shopName == null || shopName.isEmpty()
                || clickTranslateRequest.getAccessToken() == null || clickTranslateRequest.getAccessToken().isEmpty()
                || clickTranslateRequest.getSource() == null || clickTranslateRequest.getSource().isEmpty()
                || clickTranslateRequest.getTarget() == null || clickTranslateRequest.getTarget().length == 0) {
            return new BaseResponse<>().CreateErrorResponse("Missing parameters");
        }

        // TODO: 暂时使所有用户的customKey失效
        clickTranslateRequest.setCustomKey(null);

        // TODO: 改redis存储
        Map<String, Object> translationStatusMap = getTranslationStatusMap(null, 1);
        userTranslate.put(shopName, translationStatusMap);

        // 将ClickTranslateRequest转换为TranslateRequest
        TranslateRequest request = ClickTranslateRequestToTranslateRequest(clickTranslateRequest);
        ShopifyRequest shopifyRequest = convertTranslateRequestToShopifyRequest(request);

        // 判断字符是否超限
        TranslationCounterDO request1 = translationCounterService.readCharsByShopName(request.getShopName());
        Integer remainingChars = translationCounterService.getMaxCharsByShopName(request.getShopName());
        appInsights.trackTrace("clickTranslation 判断字符是否超限 : " + shopifyRequest.getShopName());

        //判断字符是否超限
        int usedChars = request1.getUsedChars();

        // 如果字符超限，则直接返回字符超限
        if (usedChars >= remainingChars) {
            return new BaseResponse<>().CreateErrorResponse(request);
        }
        appInsights.trackTrace("clickTranslation 判断字符不超限 : " + shopifyRequest.getShopName());

        // 一个用户当前只能翻译一条语言，根据用户的status判断
        appInsights.trackTrace("clickTranslation 判断用户是否有语言在翻译 : " + shopifyRequest.getShopName());
        List<Integer> integers = translatesService.readStatusInTranslatesByShopName(request.getShopName());
        if (integers.contains(2)) {
            return new BaseResponse<>().CreateErrorResponse(HAS_TRANSLATED);
        }

        // 判断是否有handle
        boolean handleFlag;
        List<String> translateModel = clickTranslateRequest.getTranslateSettings3();
        if (translateModel.contains("handle")) {
            translateModel.removeIf("handle"::equals);
            handleFlag = true;
        } else {
            handleFlag = false;
        }
        appInsights.trackTrace("clickTranslation " + shopName + " 用户现在开始翻译 要翻译的数据 " + clickTranslateRequest.getTranslateSettings3() + " handleFlag: " + handleFlag + " isCover: " + clickTranslateRequest.getIsCover());

        // 修改模块的排序
        List<String> translateResourceDTOS = translateModel(translateModel);
        appInsights.trackTrace("clickTranslation 修改模块的排序成功 : " + shopifyRequest.getShopName());

        // 翻译
        if (translateResourceDTOS == null || translateResourceDTOS.isEmpty()) {
            return new BaseResponse<>().CreateErrorResponse(clickTranslateRequest);
        }

        // 在这里异步 全部走DB翻译
        executorService.submit(() -> {
            // 循环同步 判断用户的语言是否在数据库中，在不做操作，不在，进行同步
            translateService.isExistInDatabase(shopName, clickTranslateRequest, request);
            appInsights.trackTrace("clickTranslation 循环同步 : " + shopifyRequest.getShopName());

            // 存翻译参数到db
            rabbitMqTranslateService.mqTranslateWrapper(clickTranslateRequest, shopifyRequest, translateResourceDTOS, request, handleFlag, clickTranslateRequest.getIsCover(), clickTranslateRequest.getCustomKey(), clickTranslateRequest.getTarget());
        });

        return new BaseResponse<>().CreateSuccessResponse(clickTranslateRequest);
    }

    //暂停翻译
    @DeleteMapping("/stop")
    public void stop(@RequestParam String shopName) {
        translateService.stopTranslation(shopName);
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

        //判断用户的语言是否在数据库中，在不做操作，不在，进行同步
        TranslatesDO one = translatesService.getOne(new QueryWrapper<TranslatesDO>().eq("shop_name", request.getShopName()).eq("source", request.getSource()).eq("target", request.getTarget()));
        if (one == null) {
            //走同步逻辑
            translateService.syncShopifyAndDatabase(new TranslateRequest(0, request.getShopName(), null, request.getSource(), request.getTarget(), null));
        }
        return translatesService.updateAutoTranslateByShopName(request.getShopName(), request.getAutoTranslate(), request.getSource(), request.getTarget());
    }

    //单条文本翻译
    @PostMapping("/singleTextTranslate")
    public BaseResponse<Object> singleTextTranslate(@RequestBody SingleTranslateVO singleTranslateVO) {
        return translateService.singleTextTranslate(singleTranslateVO);
    }

    /**
     * 获取当前用户的value值
     */
    @GetMapping("/getUserValue")
    public BaseResponse<Object> getUserValue(@RequestParam String shopName) {
        if (beforeUserTranslate.isEmpty()) {
            beforeUserTranslate.put(shopName, getTranslationStatusMap("Searching for content to translate…", 2));
        }
        if (userTranslate.isEmpty()) {
            userTranslate.put(shopName, getTranslationStatusMap("Searching for content to translate…", 2));
        }
        //获取当前用户前一次的value值
        Map<String, Object> map = beforeUserTranslate.get(shopName);
        Map<String, Object> value = userTranslate.get(shopName);
        if (value == null) {
            Map<String, Object> translationStatusMap = getTranslationStatusMap("Searching for content to translate…", 2);
            return new BaseResponse<>().CreateSuccessResponse(translationStatusMap);
        }
        if (value.get("value") == null && value.get("status").equals(3)) {
            return new BaseResponse<>().CreateSuccessResponse(value);
        }

        if (value.get("value") == null && value.get("status").equals(2)) {
            Map<String, Object> translationStatusMap = getTranslationStatusMap("Searching for content to translate…", 2);
            return new BaseResponse<>().CreateSuccessResponse(translationStatusMap);
        }
        if (map == null) {
            beforeUserTranslate.put(shopName, value);
        } else {
            //判断beforeUserTranslate与userTranslate里面的数据是否相同，相同的话，返回
            value.putIfAbsent("value", "Searching for content to translate…");
            if (map.get("value").equals(value.get("value"))) {
                value.put("value", "Searching for content to translate…");
            }
        }
        return new BaseResponse<>().CreateSuccessResponse(value);
    }

    /**
     * 停止翻译按钮
     * 将所有状态0和状态2的任务改成7
     */
    @PutMapping("/stopTranslatingTask")
    public BaseResponse<Object> stopTranslatingTask(@RequestParam String shopName, @RequestBody TranslatingStopVO translatingStopVO) {
        appInsights.trackTrace("stopTranslatingTask 正在翻译的用户： " + userStopFlags);
        AtomicBoolean stopFlag = userStopFlags.get(shopName);
        stopFlag.set(true);  // 设置停止标志，任务会在合适的地方检查并终止
        userStopFlags.put(shopName, stopFlag);
        //获取所有的status为2的target
        List<TranslatesDO> list = translatesService.list(new LambdaQueryWrapper<TranslatesDO>().eq(TranslatesDO::getShopName, shopName).eq(TranslatesDO::getStatus, 2).eq(TranslatesDO::getSource, translatingStopVO.getSource()).orderByAsc(TranslatesDO::getUpdateAt));
        //将所有状态2的任务改成7
        translatesService.updateStopStatus(shopName, translatingStopVO.getSource(), translatingStopVO.getAccessToken());
        //将所有状态为0和2的子任务，改为7
        Boolean flag = iTranslateTasksService.updateStatus0And2To7(shopName);
        if (flag && stopFlag.get()) {
            //将redis进度条删除掉
            for (TranslatesDO translatesDO : list
            ) {
                redisProcessService.initProcessData(generateProcessKey(shopName, translatesDO.getTarget()));
            }
            appInsights.trackTrace("stopTranslatingTask " + shopName + " 停止成功");
            return new BaseResponse<>().CreateSuccessResponse(stopFlag);
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
