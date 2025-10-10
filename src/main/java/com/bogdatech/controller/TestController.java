package com.bogdatech.controller;


import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.bogdatech.Service.IAPGUsersService;
import com.bogdatech.Service.ICharsOrdersService;
import com.bogdatech.Service.ITranslateTasksService;
import com.bogdatech.Service.ITranslationCounterService;
import com.bogdatech.Service.impl.TranslatesServiceImpl;
import com.bogdatech.entity.DO.*;
import com.bogdatech.entity.DTO.KeyValueDTO;
import com.bogdatech.entity.VO.GptVO;
import com.bogdatech.entity.VO.RabbitMqTranslateVO;
import com.bogdatech.entity.VO.UserDataReportVO;
import com.bogdatech.integration.ChatGptIntegration;
import com.bogdatech.integration.RateHttpIntegration;
import com.bogdatech.integration.RedisIntegration;
import com.bogdatech.logic.*;
import com.bogdatech.logic.redis.TranslationMonitorRedisService;
import com.bogdatech.model.controller.request.ClickTranslateRequest;
import com.bogdatech.model.controller.request.CloudServiceRequest;
import com.bogdatech.model.controller.request.ShopifyRequest;
import com.bogdatech.model.controller.request.TranslateRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.model.service.ProcessDbTaskService;
import com.bogdatech.task.DBTask;
import com.bogdatech.utils.CharacterCountUtils;
import com.bogdatech.utils.TimeOutUtils;
import com.microsoft.applicationinsights.TelemetryClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import static com.bogdatech.entity.DO.TranslateResourceDTO.TOKEN_MAP;
import static com.bogdatech.integration.RateHttpIntegration.rateMap;
import static com.bogdatech.integration.ShopifyHttpIntegration.getInfoByShopify;
import static com.bogdatech.logic.TranslateService.*;
import static com.bogdatech.task.GenerateDbTask.GENERATE_SHOP;
import static com.bogdatech.utils.AESUtils.encryptMD5;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.JudgeTranslateUtils.*;
import static com.bogdatech.utils.MapUtils.getTranslationStatusMap;
import static com.bogdatech.utils.ModelUtils.translateModel;
import static com.bogdatech.utils.RedisKeyUtils.*;
import static com.bogdatech.utils.StringUtils.*;
import static com.bogdatech.utils.TypeConversionUtils.ClickTranslateRequestToTranslateRequest;
import static com.bogdatech.utils.TypeConversionUtils.convertTranslateRequestToShopifyRequest;

@RestController
public class TestController {
    @Autowired
    private TranslatesServiceImpl translatesServiceImpl;
    @Autowired
    private ChatGptIntegration chatGptIntegration;
    @Autowired
    private TaskService taskService;
    @Autowired
    private RateHttpIntegration rateHttpIntegration;
    @Autowired
    private UserTypeTokenService userTypeTokenService;
    @Autowired
    private ProcessDbTaskService processDbTaskService;
    @Autowired
    private TencentEmailService tencentEmailService;
    @Autowired
    private ITranslateTasksService translateTasksService;
    @Autowired
    private DBTask dBTask;
    @Autowired
    private UserTranslationDataService userTranslationDataService;
    @Autowired
    private IAPGUsersService iapgUsersService;
    @Autowired
    private RedisProcessService redisProcessService;
    @Autowired
    private RedisDataReportService redisDataReportService;
    @Autowired
    private RedisTranslateLockService redisTranslateLockService;
    @Autowired
    private RedisIntegration redisIntegration;
    @Autowired
    private RabbitMqTranslateService rabbitMqTranslateService;
    @Autowired
    private ITranslationCounterService translationCounterService;


    @GetMapping("/ping")
    public String ping() {
        TelemetryClient appInsights = new TelemetryClient();
        appInsights.trackTrace("SpringBackend Ping Successful");
        return "Ping Successful!";
    }

    @PostMapping("/gpt")
    public String chat(@RequestBody GptVO gptVO) {
        return chatGptIntegration.chatWithGpt(gptVO.getPrompt(), gptVO.getSourceText(), new TranslateRequest(0, "ciwishop.myshopify.com", null, "en", "zh-CN", gptVO.getSourceText()), new CharacterCountUtils(), 2000000);
    }

    @PostMapping("/test/test1")
    public int test1(@RequestBody TranslatesDO name) {
        return translatesServiceImpl.updateTranslateStatus(name.getShopName(), name.getStatus(), name.getTarget(), name.getSource(), name.getAccessToken());
    }

    //通过测试环境调shopify的API
    @PostMapping("/test123")
    public String test(@RequestBody CloudServiceRequest cloudServiceRequest) {
        ShopifyRequest request = new ShopifyRequest();
        request.setShopName(cloudServiceRequest.getShopName());
        request.setAccessToken(cloudServiceRequest.getAccessToken());
        request.setTarget(cloudServiceRequest.getTarget());
        String body = cloudServiceRequest.getBody();
        JSONObject infoByShopify = getInfoByShopify(request, body);
        if (infoByShopify == null || infoByShopify.isEmpty()) {
            return null;
        }
        return infoByShopify.toString();
    }


    //发送成功翻译的邮件gei
    @GetMapping("/sendEmail")
    public void sendEmail() {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        tencentEmailService.sendAPGSuccessEmail("daoyee@ciwi.ai", 1L, "product", "daoyee", now, 9989, 10, 100001);
    }

    //获取汇率
    @GetMapping("/getRate")
    public void getRate() {
        rateHttpIntegration.getFixerRate();
        appInsights.trackTrace("rateMap: " + rateMap.toString());
    }

    //测试获取缓存功能
    @GetMapping("/testCache")
    public String testCache(@RequestParam String target, @RequestParam String value) {
        return redisProcessService.getCacheData(target, value);
    }

    @GetMapping("/testAddCache")
    public void testAddCache(String target, String value, String targetText) {
        redisProcessService.setCacheData(target, targetText, value);
    }

    //测试theme判断
    @PostMapping("/testKeyValue")
    public String testKeyValue(@RequestBody KeyValueDTO keyValueDTO) {
        String key = keyValueDTO.getKey();
        String value = keyValueDTO.getValue();
        if (SUSPICIOUS2_PATTERN.matcher(value).matches()) {
            return "Metafield不翻译";
        }

        //通用的不翻译数据
        if (!generalTranslate(key, value)) {
            return "通用不翻译";
        }

        //如果是theme模块的数据
        if (GENERAL_OR_SECTION_PATTERN.matcher(key).find()) {
            //进行白名单的确认
            if (whiteListTranslate(key)) {
                return "白名单翻译";
            }
            //如果包含对应key和value，则跳过
            if (!shouldTranslate(key, value)) {
                return "theme不翻译";
            }
        }

        return "需要翻译";
    }

    //测试自动翻译功能
    @PutMapping("/testAutoTranslate")
    public void testAutoTranslate() {
        appInsights.trackTrace("testAutoTranslate 开始调用");
        executorService.execute(() -> {
            taskService.autoTranslate();
        });
    }

    @GetMapping("/testFreeTrialTask")
    public void testFreeTrialTask() {
        taskService.freeTrialTask();
    }

    @PostMapping("/testToken")
    public void testToken(@RequestBody ShopifyRequest request) {
        for (String key : TOKEN_MAP.keySet()
        ) {
            userTypeTokenService.testTokenCount(request, key);
        }
        appInsights.trackTrace("统计结束！！！");
    }

    @GetMapping("/testHandle")
    public String testHandle(@RequestParam String value) {
        return replaceHyphensWithSpaces(value);
    }

    @PutMapping("/testThread")
    public String logThreadPoolStatus(@RequestParam String shopName) {
        if (executorService instanceof ThreadPoolExecutor executor) {
            String userTranslates = (" - 用户翻译Set： " + userTranslate + " ");
            String process = (" - 进程Set： " + redisIntegration.get(generateTranslateLockKey(shopName)) + " ");
            String userStopFlag = (" - 停止标志： " + userStopFlags);
            String executorServic = (" - 线程池状态：" + executorService + "  ");
            String crePoolSize = (" - 核心线程数(corePoolSize): " + executor.getCorePoolSize() + "  ");
            String maximumPoolSize = (" - 最大线程数(maximumPoolSize): " + executor.getMaximumPoolSize() + "  ");
            String poolSize = (" - 当前线程池中线程数(poolSize): " + executor.getPoolSize() + "  ");
            String activeCount = (" - 活跃线程数(activeCount): " + executor.getActiveCount() + "  ");
            String completedTaskCount = (" - 已完成任务数(completedTaskCount): " + executor.getCompletedTaskCount() + "  ");
            String taskCount = (" - 总任务数(taskCount): " + executor.getTaskCount() + "  ");
            String queue = (" - 当前排队任务数(queue size): " + executor.getQueue().size() + "  ");
            String remainingCapacity = (" - 队列剩余容量(remaining capacity): " + executor.getQueue().remainingCapacity() + "  ");
            String allowsCoreThreadTimeOut = (" - 是否允许核心线程超时(allowsCoreThreadTimeOut): " + executor.allowsCoreThreadTimeOut() + "  ");
            String keepAliveTime = (" - 线程空闲存活时间(keepAliveTime): " + executor.getKeepAliveTime(TimeUnit.SECONDS) + " 秒" + "  ");
            String shutdown = (" - 是否已关闭(isShutdown): " + executor.isShutdown() + "  ");
            String terminated = (" - 是否终止(isTerminated): " + executor.isTerminated() + "  ");
            String terminating = (" - 正在终止中(isTerminating): " + executor.isTerminating() + "  ");
            return userTranslates + process + userStopFlag + executorServic + crePoolSize + maximumPoolSize + poolSize + activeCount + completedTaskCount + taskCount + queue + remainingCapacity + allowsCoreThreadTimeOut + keepAliveTime + shutdown + terminated + terminating;
        }
        return null;
    }

    // 停止mq翻译任务
    @PutMapping("/stopMqTask")
    public void stopMqTask(@RequestParam String shopName) {
        appInsights.trackTrace("正在翻译的用户： " + userStopFlags);
        AtomicBoolean stopFlag = userStopFlags.get(shopName);
        if (stopFlag != null) {
            stopFlag.set(true);  // 设置停止标志，任务会在合适的地方检查并终止
            appInsights.trackTrace("停止成功");
        }
    }


    /**
     * 输入任务id，实现该任务的翻译
     */
    @PutMapping("/testDBTranslate2")
    public void testDBTranslate2(@RequestParam String taskId) {
        //根据id获取数据，转化为规定数据类型
        RabbitMqTranslateVO dataToProcess = translateTasksService.getDataToProcess(taskId);
        processDbTaskService.processTask(dataToProcess, new TranslateTasksDO(), false);
        translateTasksService.updateByTaskId(taskId, 1);
    }

    /**
     * 启动DB翻译
     */
    @PutMapping("/testDBTranslate")
    public void testDBTranslate() {
        dBTask.scanAndSubmitTasks();
    }

    /**
     * 修改stopFlag
     */
    @PutMapping("/testStopFlagToTure")
    public String testStopFlagToTure(@RequestParam String shopName, @RequestParam Boolean flag) {
        userStopFlags.put(shopName, new AtomicBoolean(flag));
        return userStopFlags.toString();
    }

    /**
     * 测试开头为general或section的判断
     */
    @PutMapping("/testGeneralOrSection")
    public String testGeneralOrSection(@RequestParam String key) {
        if (GENERAL_OR_SECTION_PATTERN.matcher(key).find()) {
            return "true";
        } else {
            return "false";
        }
    }

    /**
     * 一键式恢复用户翻译
     */
    @PutMapping("/testRecover")
    public String testRecover(@RequestParam String shopName) {
        //获取用户，redis锁情况
        translateTasksService.update(new UpdateWrapper<TranslateTasksDO>().eq("shop_name", shopName).and(wrapper -> wrapper.eq("status", 2)).set("status", 4));
        boolean flag = redisTranslateLockService.setRemove(shopName);
        Map<String, Object> translationStatusMap = getTranslationStatusMap(" ", 1);
        userTranslate.put(shopName, translationStatusMap);
        return "是否解锁成功： " + flag;
    }

    /**
     * 测试加锁
     */
    @GetMapping("/testLock")
    public Boolean testLock(@RequestParam String shopName) {
        return redisTranslateLockService.setAdd(shopName);
    }

    @GetMapping("/testUnlock")
    public Boolean testUnlock(@RequestParam String shopName) {
        return redisTranslateLockService.setRemove(shopName);
    }


    @GetMapping("/testReadList")
    public List<UserTranslationDataDO> testreadList() {
        return userTranslationDataService.selectTranslationDataList();
    }

    @GetMapping("/testRemove")
    public boolean testRemove(@RequestParam String taskId) {
        return userTranslationDataService.updateStatusTo2(taskId, 2);
    }

    /**
     * 往缓存中插入数据
     */
    @GetMapping("/testInsertCache")
    public void testInsertCache(@RequestParam String shopName, @RequestParam String value) {
        Map<String, Object> translationStatusMap = getTranslationStatusMap(value, 2);
        userTranslate.put(shopName, translationStatusMap);
    }

    /**
     * 暂停APG应用的生成任务
     */
    @GetMapping("/testAPGStop")
    public boolean userMaxLimit(@RequestParam String shopName) {
        APGUsersDO usersDO = iapgUsersService.getOne(new LambdaQueryWrapper<APGUsersDO>().eq(APGUsersDO::getShopName, shopName));
        return GENERATE_SHOP.add(usersDO.getId());
    }

    /**
     * 单纯的打印信息
     */
    @PostMapping("/frontEndPrinting")
    public void frontEndPrinting(@RequestBody String data) {
        appInsights.trackTrace(data);
    }

    /**
     * 数据上传
     */
    @PostMapping("/saveUserDataReport")
    public void userDataReport(@RequestParam String shopName, @RequestBody UserDataReportVO userDataReportVO) {
        redisDataReportService.saveUserDataReport(shopName, userDataReportVO);
    }


    /**
     * 读取相关数据
     */
    @PostMapping("/getUserDataReport")
    public BaseResponse<Object> getUserDataReport(@RequestParam String shopName, @RequestBody UserDataReportVO userDataReportVO) {
        String userDataReport = redisDataReportService.getUserDataReport(shopName, userDataReportVO.getStoreLanguage(), userDataReportVO.getTimestamp(), userDataReportVO.getDayData());
        if (userDataReport != null) {
            return new BaseResponse<>().CreateSuccessResponse(userDataReport);
        }
        return new BaseResponse<>().CreateErrorResponse(false);
    }

    /**
     * 加密后输出数据
     * */
    @GetMapping("/testEncryptMD5")
    public String testEncryptMD5(@RequestParam String source) {
        return encryptMD5(source);
    }

    /**
     * 测试时间超时的问题
     * */
    @GetMapping("/testTimeOut")
    public void testTimeOut() throws Exception {
        String s = TimeOutUtils.callWithTimeoutAndRetry(() -> {
            // 模拟耗时操作
            LocalDateTime first = LocalDateTime.now();
            System.out.println("first: " + first);
            try {
                Thread.sleep(310000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            LocalDateTime second = LocalDateTime.now();
            System.out.println("second: " + second);
            return "任务完成";
        }, 5, TimeUnit.MINUTES, 3);
        System.out.println("结果: " + s);
    }

    @Autowired
    private TranslationMonitorRedisService translationMonitorRedisService;

    @Autowired
    private ICharsOrdersService charsOrdersService;

    @GetMapping("/getTable")
    public Map<String, Object> getTable(@RequestParam String shopName) {
        List<CharsOrdersDO> list = charsOrdersService.getCharsOrdersDoByShopName(shopName);
        Map<String, Object> map = new HashMap<>();
        map.put("CharsOrder", list);
        return map;
    }

    @GetMapping("/monitor")
    public Map<String, Object> monitor() {
        Map<String, Object> responseMap = new HashMap<>();

        List<TranslatesDO> startedTasks = translatesServiceImpl.getStatus2Data();
        Set<String> startedShops = startedTasks.stream().map(TranslatesDO::getShopName).collect(Collectors.toSet());
        responseMap.put("Step1-有进度条的用户", startedShops);

        // 统计待翻译的 task
        List<TranslateTasksDO> tasks = translateTasksService.find0StatusTasks();
        responseMap.put("总的子任务数量", tasks.size());

        // 统计shopName数量
        Set<String> shops = tasks.stream().map(TranslateTasksDO::getShopName).collect(Collectors.toSet());
        responseMap.put("Step2-创建了翻译子任务的用户", shops);

        Set<String> translatingShops = redisTranslateLockService.members();
        responseMap.put("Step3-翻译中的用户", translatingShops);

        for (String shop : shops) {
            Set<TranslateTasksDO> shopTasks = tasks.stream()
                    .filter(taskDo -> taskDo.getShopName().equals(shop))
                    .collect(Collectors.toSet());

            Map map = translationMonitorRedisService.getShopTranslationStats(shop);
            map.put("翻译子任务数量", shopTasks.size());

            responseMap.put(shop, map);
//            Map<String, List<RabbitMqTranslateVO>> targetMap = shopTasks.stream()
//                    .map(translateTasksDO -> jsonToObject(translateTasksDO.getPayload(), RabbitMqTranslateVO.class))
//                    .filter(Objects::nonNull)
//                    .collect(Collectors.groupingBy(RabbitMqTranslateVO::getTarget));
//
//            targetMap.forEach((target, list) -> {
//                Map<String, List<RabbitMqTranslateVO>> moeMap = list.stream().collect(Collectors.groupingBy(RabbitMqTranslateVO::getModeType));
//                // TODO 在这里对shop下的task进行分类：语言分类，模块分类，安排不同的线程同时翻译
//            });

        }
        return responseMap;
    }

    @PostMapping("/mqTranslateWrapper")
    public BaseResponse<Object> mqTranslateWrapper(@RequestParam String shopName, @RequestBody ClickTranslateRequest clickTranslateRequest) {
        //判断前端传的数据是否完整，如果不完整，报错
        if (shopName == null || shopName.isEmpty()
                || clickTranslateRequest.getAccessToken() == null || clickTranslateRequest.getAccessToken().isEmpty()
                || clickTranslateRequest.getSource() == null || clickTranslateRequest.getSource().isEmpty()
                || clickTranslateRequest.getTarget() == null || clickTranslateRequest.getTarget().length == 0) {
            return new BaseResponse<>().CreateErrorResponse("Missing parameters");
        }

        if (clickTranslateRequest.getIsCover() == null) {
            clickTranslateRequest.setIsCover(false);
        }

        //暂时使所有用户的customKey失效
        clickTranslateRequest.setCustomKey(null);

        Map<String, Object> translationStatusMap = getTranslationStatusMap(null, 1);
        userTranslate.put(shopName, translationStatusMap);
        //将ClickTranslateRequest转换为TranslateRequest
        TranslateRequest request = ClickTranslateRequestToTranslateRequest(clickTranslateRequest);
        ShopifyRequest shopifyRequest = convertTranslateRequestToShopifyRequest(request);
        TranslationCounterDO request1 = translationCounterService.readCharsByShopName(request.getShopName());
        Integer remainingChars = translationCounterService.getMaxCharsByShopName(request.getShopName());
        int usedChars = request1.getUsedChars();
        //判断是否有handle
        boolean handleFlag;
        List<String> translateModel = clickTranslateRequest.getTranslateSettings3();
        if (translateModel.contains("handle")) {
            translateModel.removeIf("handle"::equals);
            handleFlag = true;
        } else {
            handleFlag = false;
        }
        appInsights.trackTrace("clickTranslation " + shopName + " 用户现在开始翻译 要翻译的数据 " + clickTranslateRequest.getTranslateSettings3() + " handleFlag: " + handleFlag + " isCover: " + clickTranslateRequest.getIsCover());
        //修改模块的排序
        List<String> translateResourceDTOS = null;
        try {
            translateResourceDTOS = translateModel(translateModel);
        } catch (Exception e) {
            appInsights.trackTrace("clickTranslation translateModel errors : " + e.getMessage());
        }
        appInsights.trackTrace("clickTranslation 修改模块的排序成功 : " + shopifyRequest.getShopName());
//      翻译
        if (translateResourceDTOS == null || translateResourceDTOS.isEmpty()) {
            return new BaseResponse<>().CreateErrorResponse(clickTranslateRequest);
        }

        //在这里异步 全部走DB翻译
        List<String> finalTranslateResourceDTOS = translateResourceDTOS;
        executorService.submit(() -> {
            rabbitMqTranslateService.mqTranslateWrapper(shopifyRequest, finalTranslateResourceDTOS, request, remainingChars, usedChars, handleFlag, clickTranslateRequest.getTranslateSettings1(), clickTranslateRequest.getIsCover(), clickTranslateRequest.getCustomKey(), true, clickTranslateRequest.getTarget());
        });
        return new BaseResponse<>().CreateSuccessResponse("Translation started successfully");
    }

}
