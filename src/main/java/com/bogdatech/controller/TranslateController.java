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
import com.bogdatech.entity.VO.SingleTranslateVO;
import com.bogdatech.entity.VO.TranslateArrayVO;
import com.bogdatech.entity.VO.TranslatingStopVO;
import com.bogdatech.logic.RabbitMqTranslateService;
import com.bogdatech.logic.TranslateService;
import com.bogdatech.logic.UserTypeTokenService;
import com.bogdatech.model.controller.request.*;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.utils.CharacterCountUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
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
import static com.bogdatech.utils.TypeConversionUtils.*;

@RestController
@RequestMapping("/translate")
public class TranslateController {
    private final TranslateService translateService;
    private final ITranslatesService translatesService;
    private final ITranslationCounterService translationCounterService;
    private final IUserTypeTokenService userTypeTokenService;
    private final UserTypeTokenService userTypeTokensService;
    private final RabbitMqTranslateService rabbitMqTranslateService;
    private final ITranslateTasksService iTranslateTasksService;

    @Autowired
    public TranslateController(
            TranslateService translateService,
            ITranslatesService translatesService,
            ITranslationCounterService translationCounterService,
            IUserTypeTokenService userTypeTokenService, UserTypeTokenService userTypeTokensService, RabbitMqTranslateService rabbitMqTranslateService, ITranslateTasksService iTranslateTasksService) {
        this.translateService = translateService;
        this.translatesService = translatesService;
        this.translationCounterService = translationCounterService;
        this.userTypeTokenService = userTypeTokenService;
        this.userTypeTokensService = userTypeTokensService;
        this.rabbitMqTranslateService = rabbitMqTranslateService;
        this.iTranslateTasksService = iTranslateTasksService;
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
     * 调用百度翻译的API接口
     */
    @PostMapping("/baiDuTranslate")
    public BaseResponse<Object> baiDuTranslate(@RequestBody TranslateRequest request) {
        return new BaseResponse<>().CreateSuccessResponse(translateService.baiDuTranslate(request));
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
            List<TranslatesDO> list = translatesService.list(new LambdaQueryWrapper<TranslatesDO>().eq(TranslatesDO::getShopName, request.getShopName()).eq(TranslatesDO::getSource, request.getSource()).eq(TranslatesDO::getStatus, 2).orderByAsc(TranslatesDO::getUpdateAt));
            if (list.isEmpty()) {
                TranslatesDO translatesDO = translatesService.selectLatestOne(request);
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
        //判断前端传的数据是否完整，如果不完整，报错
        if (shopName == null || shopName.isEmpty()
                || clickTranslateRequest.getAccessToken() == null || clickTranslateRequest.getAccessToken().isEmpty()
                || clickTranslateRequest.getSource() == null || clickTranslateRequest.getSource().isEmpty()
                || clickTranslateRequest.getTarget() == null || clickTranslateRequest.getTarget().length == 0) {
            return new BaseResponse<>().CreateErrorResponse("Missing parameters");
        }
        if (clickTranslateRequest.getTarget().length > 5) {
            return new BaseResponse<>().CreateErrorResponse("Please select no more than 5 languages to translate.");
        }

        //判断该用户是否

        if (clickTranslateRequest.getIsCover() == null) {
            clickTranslateRequest.setIsCover(false);
        }
        Map<String, Object> translationStatusMap = getTranslationStatusMap(null, 1);
        userTranslate.put(shopName, translationStatusMap);
        //将ClickTranslateRequest转换为TranslateRequest
        TranslateRequest request = ClickTranslateRequestToTranslateRequest(clickTranslateRequest);
        ShopifyRequest shopifyRequest = convertTranslateRequestToShopifyRequest(request);
        //判断用户的语言是否在数据库中，在不做操作，不在，进行同步
        //循环同步
        translateService.isExistInDatabase(shopName, clickTranslateRequest, request);

        //判断字符是否超限
        TranslationCounterDO request1 = translationCounterService.readCharsByShopName(request.getShopName());
        Integer remainingChars = translationCounterService.getMaxCharsByShopName(request.getShopName());

//        一个用户当前只能翻译一条语言，根据用户的status判断
        List<Integer> integers = translatesService.readStatusInTranslatesByShopName(request.getShopName());
        for (Integer integer : integers) {
            if (integer == 2) {
                return new BaseResponse<>().CreateErrorResponse(HAS_TRANSLATED);
            }
        }

        //判断字符是否超限
        int usedChars = request1.getUsedChars();

        // 如果字符超限，则直接返回字符超限
        if (usedChars >= remainingChars) {
            return new BaseResponse<>().CreateErrorResponse(request);
        }

        userEmailStatus.put(shopName, new AtomicBoolean(false)); //重置用户发送的邮件
        userStopFlags.put(shopName, new AtomicBoolean(false));  // 初始化用户的停止标志

        //初始化计数器
        CharacterCountUtils counter = new CharacterCountUtils();
        counter.addChars(usedChars);

        //判断是否有handle
        boolean handleFlag = false;
        List<String> translateModel = clickTranslateRequest.getTranslateSettings3();
        if (translateModel.contains("handle")) {
            translateModel.removeIf("handle"::equals);
            handleFlag = true;
        }
        appInsights.trackTrace(shopName + " 用户 要翻译的数据 " + clickTranslateRequest.getTranslateSettings3() + " handleFlag: " + handleFlag + " isCover: " + clickTranslateRequest.getIsCover());
        //修改模块的排序
        List<String> translateResourceDTOS = null;
        try {
            translateResourceDTOS = translateModel(translateModel);
        } catch (Exception e) {
            appInsights.trackTrace("translateModel errors : " + e.getMessage());
        }
//      翻译
        if (translateResourceDTOS == null || translateResourceDTOS.isEmpty()) {
            return new BaseResponse<>().CreateErrorResponse(clickTranslateRequest);
        }

        //修改自定义提示词
        String fixCustomKey = clickTranslateRequest.getCustomKey();
        String cleanedText = null;
        if (fixCustomKey != null){
            cleanedText = fixCustomKey.replaceAll("\\.{2,}", ".");
        }

        //改为循环遍历，将相关target状态改为2
        String[] targets = clickTranslateRequest.getTarget();
        for (int i = targets.length - 1; i >= 0; i--) {
            String target = targets[i];
            translatesService.updateTranslateStatus(
                    request.getShopName(),
                    2,
                    target,
                    request.getSource(),
                    request.getAccessToken()
            );
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                appInsights.trackException(e);
            }
        }

        //全部走DB翻译
        rabbitMqTranslateService.mqTranslateWrapper(shopifyRequest, counter, translateResourceDTOS, request, remainingChars, usedChars, handleFlag, clickTranslateRequest.getTranslateSettings1(), clickTranslateRequest.getIsCover(), cleanedText, true, clickTranslateRequest.getTarget());
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
        appInsights.trackTrace("用户 ： " + cloudServiceRequest.getShopName() + " insertTranslatedText : " + s);
    }


    //删除翻译状态的语言
    @PostMapping("/deleteFromTranslates")
    public BaseResponse<Object> deleteFromTranslates(@RequestBody TranslateRequest request) {
        Boolean b = translatesService.deleteFromTranslates(request);
        if (b) {
            return new BaseResponse<>().CreateSuccessResponse(200);
        } else {
            return new BaseResponse<>().CreateErrorResponse(SQL_DELETE_ERROR);
        }
    }

    //将缓存的数据存到数据库中
    @PostMapping("/saveCacheToTranslates")
    public String saveToTranslates() {
        translateService.saveToTranslates();
        return SINGLE_LINE_TEXT.toString();
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
        if (beforeUserTranslate.isEmpty()){
            beforeUserTranslate.put(shopName, getTranslationStatusMap("Searching for content to translate…", 2));
        }
        if (userTranslate.isEmpty()){
            userTranslate.put(shopName, getTranslationStatusMap("Searching for content to translate…", 2));
        }
        //获取当前用户前一次的value值
        Map<String, Object> map = beforeUserTranslate.get(shopName);
        Map<String, Object> value = userTranslate.get(shopName);
        if (value == null) {
            Map<String, Object> translationStatusMap = getTranslationStatusMap("Searching for content to translate…", 2);
            return new BaseResponse<>().CreateSuccessResponse(translationStatusMap);
        }
        if (value.get("value") == null && value.get("status").equals(2)) {
            Map<String, Object> translationStatusMap = getTranslationStatusMap("Searching for content to translate…", 2);
            return new BaseResponse<>().CreateSuccessResponse(translationStatusMap);
        }
        if (map == null) {
            beforeUserTranslate.put(shopName, value);
        }else {
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
        appInsights.trackTrace("正在翻译的用户： " + userStopFlags);
        AtomicBoolean stopFlag = userStopFlags.get(shopName);
        stopFlag.set(true);  // 设置停止标志，任务会在合适的地方检查并终止
        userStopFlags.put(shopName, stopFlag);
        //将所有状态2的任务改成7
        translatesService.updateStopStatus(shopName, translatingStopVO.getSource(), translatingStopVO.getAccessToken());
        //将所有状态为0和2的子任务，改为7
        Boolean flag = iTranslateTasksService.updateStatus0And2To7(shopName);
        if (flag && stopFlag.get()) {
            appInsights.trackTrace(shopName + " 停止成功");
            return new BaseResponse<>().CreateSuccessResponse(stopFlag);
        }
        return new BaseResponse<>().CreateErrorResponse(false);
    }

    /**
     * 用于获取进度条的相关数据
     * */
    @PostMapping("/getProgressData")
    public BaseResponse<Object> getProgressData(@RequestParam String shopName, @RequestParam String target) {
        Map<String, Integer> progressData = translateService.getProgressData(shopName, target);
        if (progressData != null){
            return new BaseResponse<>().CreateSuccessResponse(progressData);
        }

        return new BaseResponse<>().CreateErrorResponse(false);
    }
}
