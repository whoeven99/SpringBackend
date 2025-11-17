package com.bogdatech.controller;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.bogdatech.Service.*;
import com.bogdatech.Service.impl.TranslatesServiceImpl;
import com.bogdatech.entity.DO.*;
import com.bogdatech.entity.DTO.KeyValueDTO;
import com.bogdatech.entity.VO.GptVO;
import com.bogdatech.entity.VO.UserDataReportVO;
import com.bogdatech.integration.AidgeIntegration;
import com.bogdatech.integration.HuoShanIntegration;
import com.bogdatech.integration.RateHttpIntegration;
import com.bogdatech.integration.ShopifyHttpIntegration;
import com.bogdatech.logic.*;
import com.bogdatech.logic.redis.TranslationCounterRedisService;
import com.bogdatech.logic.redis.TranslationMonitorRedisService;
import com.bogdatech.logic.redis.TranslationParametersRedisService;
import com.bogdatech.logic.translate.TranslateDataService;
import com.bogdatech.logic.translate.TranslateProgressService;
import com.bogdatech.mapper.InitialTranslateTasksMapper;
import com.bogdatech.model.controller.request.CloudServiceRequest;
import com.bogdatech.model.controller.request.ShopifyRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.model.controller.response.ProgressResponse;
import com.bogdatech.task.AutoTranslateTask;
import com.bogdatech.task.DBTask;
import com.bogdatech.task.TranslateTask;
import com.bogdatech.utils.AESUtils;
import com.bogdatech.utils.TimeOutUtils;
import com.microsoft.applicationinsights.TelemetryClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import static com.bogdatech.entity.DO.TranslateResourceDTO.TOKEN_MAP;
import static com.bogdatech.integration.RateHttpIntegration.rateMap;
import static com.bogdatech.integration.ShopifyHttpIntegration.getInfoByShopify;
import static com.bogdatech.logic.TranslateService.*;
import static com.bogdatech.logic.redis.TranslationParametersRedisService.generateProgressTranslationKey;
import static com.bogdatech.task.GenerateDbTask.GENERATE_SHOP;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.JudgeTranslateUtils.*;
import static com.bogdatech.utils.StringUtils.*;

@RestController
public class TestController {
    @Autowired
    private TranslatesServiceImpl translatesServiceImpl;
    @Autowired
    private TaskService taskService;
    @Autowired
    private RateHttpIntegration rateHttpIntegration;
    @Autowired
    private UserTypeTokenService userTypeTokenService;
    @Autowired
    private AidgeIntegration aidgeIntegration;
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
    private ITranslationCounterService translationCounterService;
    @Autowired
    private TranslationParametersRedisService translationParametersRedisService;
    @Autowired
    private ITranslatesService iTranslatesService;
    @Autowired
    private TranslationCounterRedisService translationCounterRedisService;
    @Autowired
    private AutoTranslateTask autoTranslate;
    @Autowired
    private ITranslatesService translatesService;
    @Autowired
    private RabbitMqTranslateService rabbitMqTranslateService;
    @Autowired
    private HuoShanIntegration huoShanIntegration;

    @GetMapping("/ping")
    public String ping() {
        TelemetryClient appInsights = new TelemetryClient();
        appInsights.trackTrace("SpringBackend Ping Successful");
        return "Ping Successful!";
    }

    @PostMapping("/gpt")
    public String chat(@RequestBody GptVO gptVO) {
//        return chatGptIntegration.chatWithGpt(gptVO.getPrompt(), gptVO.getSourceText(), "ciwishop.myshopify.com", null, new CharacterCountUtils(), 2000000, false);
        return "";
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

    @Autowired
    private TranslateTask translateTask;

    @GetMapping("/autov2")
    public String testAutoTranslateV2(@RequestParam String type) {
        if ("1".equals(type)) {
            appInsights.trackTrace("autoTranslateV2 开始调用");
            List<TranslatesDO> translatesDOList = translatesService.readAllTranslates();
            appInsights.trackTrace("autoTranslateV2 任务总数: " + translatesDOList.size());

            if (CollectionUtils.isEmpty(translatesDOList)) {
                return "no task";
            }
            for (TranslatesDO translatesDO : translatesDOList) {
                appInsights.trackTrace("autoTranslateV2 测试开始一个： " + translatesDO.getShopName());
                taskService.autoTranslate(translatesDO.getShopName(), translatesDO.getSource(), translatesDO.getTarget());
            }
            return "1";
        }
        if ("2".equals(type)) {
            translateTask.initialToTranslateTask();
            return "2";
        }
        if ("3".equals(type)) {
            translateTask.translateEachTask();
            return "3";
        }
        if ("4".equals(type)) {
            translateTask.saveToShopify();
            return "4";
        }
        if ("5".equals(type)) {
            translateTask.sendEmail();
            return "5";
        }
        return "failed";
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

    // 停止mq翻译任务
    @PutMapping("/stopMqTask")
    public void stopMqTask(@RequestParam String shopName) {
        Boolean stopFlag = translationParametersRedisService.delStopTranslationKey(shopName);
        if (stopFlag) {
            appInsights.trackTrace("停止成功");
        }
    }

    /**
     * 输入任务id，实现该任务的翻译
     */
    @PutMapping("/testDBTranslate2")
    public void testDBTranslate2(@RequestParam String taskId) {
        //根据id获取数据，转化为规定数据类型
//        RabbitMqTranslateVO dataToProcess = translateTasksService.getDataToProcess(taskId);
//        processDbTaskService.processTask(dataToProcess, new TranslateTasksDO(), false);
//        translateTasksService.updateByTaskId(taskId, 1);
    }

    /**
     * 启动DB翻译
     */
    @PutMapping("/testDBTranslate")
    public void testDBTranslate() {
        dBTask.scanAndSubmitTasks();
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
        String userDataReport = redisDataReportService.getUserDataReport(shopName, userDataReportVO.getTimestamp(), userDataReportVO.getDayData());
        if (userDataReport != null) {
            return new BaseResponse<>().CreateSuccessResponse(userDataReport);
        }
        return new BaseResponse<>().CreateErrorResponse(false);
    }

    /**
     * 加密后输出数据
     */
    @GetMapping("/testEncryptMD5")
    public String testEncryptMD5(@RequestParam String source) {
        return AESUtils.encryptMD5(source);
    }

    /**
     * 测试时间超时的问题
     */
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

        // 获取Translates表数据
        List<TranslatesDO> translatesDOS = iTranslatesService.listTranslatesDOByShopName(shopName);
        map.put("Translates", translatesDOS);

        // 获取task表准备翻译和正在翻译的数据
        List<TranslateTasksDO> translateTasksDOS = translateTasksService.listTranslateStatus2And0TasksByShopName(shopName);
        map.put("TranslateTasks", translateTasksDOS);

        // 获取用户额度表数据
        TranslationCounterDO translationCounterDO = translationCounterService.getTranslationCounterByShopName(shopName);
        map.put("TranslationCounter", new ArrayList<TranslationCounterDO>() {{
            add(translationCounterDO);
        }});

        // 获取initial表的数据
        List<InitialTranslateTasksDO> initialTranslateTasksDOS = initialTranslateTasksMapper.selectList(
                new LambdaQueryWrapper<InitialTranslateTasksDO>()
                        .eq(InitialTranslateTasksDO::getShopName, shopName)
                        .eq(InitialTranslateTasksDO::isDeleted, false));
        map.put("InitialTranslateTasks", initialTranslateTasksDOS);

        return map;
    }

    @Autowired
    private TranslateProgressService translateProgressService;

    // For Monitor
    @GetMapping("/getProgressByShopName")
    public List<ProgressResponse.Progress> getProgressByShopName(@RequestParam String shopName) {
        List<InitialTranslateTasksDO> initialTranslateTasksDOS = initialTranslateTasksMapper.selectList(
                new LambdaQueryWrapper<InitialTranslateTasksDO>()
                        .eq(InitialTranslateTasksDO::getShopName, shopName)
                        .eq(InitialTranslateTasksDO::isDeleted, false)
                        .orderByAsc(InitialTranslateTasksDO::getCreatedAt));
        if (initialTranslateTasksDOS.isEmpty()) {
            return new ArrayList<>();
        }

        return translateProgressService.getAllProgressData(shopName, initialTranslateTasksDOS.get(0).getSource()).getResponse().getList();
    }

    @Autowired
    private InitialTranslateTasksMapper initialTranslateTasksMapper;

    @GetMapping("/monitor")
    public Map<String, Object> monitor() {
        // Initial Task 未开始，进行中的任务
        List<InitialTranslateTasksDO> initialTranslateTasksDOS0 = initialTranslateTasksMapper.selectList(
                new LambdaQueryWrapper<InitialTranslateTasksDO>()
                        .eq(InitialTranslateTasksDO::getStatus, 0)
                        .orderByAsc(InitialTranslateTasksDO::getCreatedAt));

        List<InitialTranslateTasksDO> initialTranslateTasksDOS2 = initialTranslateTasksMapper.selectList(
                new LambdaQueryWrapper<InitialTranslateTasksDO>()
                        .eq(InitialTranslateTasksDO::getStatus, 2)
                        .orderByAsc(InitialTranslateTasksDO::getCreatedAt));

        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("Step0-创建总翻译任务的用户-未开始（则说明有问题）", initialTranslateTasksDOS0.stream().map(InitialTranslateTasksDO::getShopName).collect(Collectors.toSet()));
        responseMap.put("Step0-创建总翻译任务的用户-进行中", initialTranslateTasksDOS2.stream().map(InitialTranslateTasksDO::getShopName).collect(Collectors.toSet()));

        List<TranslatesDO> startedTasks = translatesServiceImpl.getStatus2Data();
        Set<String> startedShops = startedTasks.stream().map(TranslatesDO::getShopName).collect(Collectors.toSet());
        responseMap.put("Step1-有进度条的用户", startedShops);

        // 统计待翻译的 task
        List<TranslateTasksDO> tasks = translateTasksService.find0StatusTasks();
        responseMap.put("总的子任务数量", tasks.size());

        List<TranslatesDO> translatesDOList = translatesService.readAllTranslates();
        responseMap.put("自动翻译的任务数量", translatesDOList.size());

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

    /**
     * 获取redis中的进度条数据
     */
    @GetMapping("/getRedisTranslationData")
    public Map<String, String> getRedisTranslationData(@RequestParam String shopName, @RequestParam String source, @RequestParam String target) {
        return translationParametersRedisService.getProgressTranslationKey(generateProgressTranslationKey(shopName, source, target));
    }

    /**
     * 递增
     */
    @GetMapping("/increase")
    public Long increase(@RequestParam String shopName, @RequestParam String target, @RequestParam long value, @RequestParam String type) {
        return translationCounterRedisService.increaseLanguage(shopName, target, value, type);
    }

    /**
     * 手动启动自动翻译
     */
    @PutMapping("/startAuto")
    public void startAuto() {
        autoTranslate.autoTranslate();
    }

    // 测试glossary缓存
    @GetMapping("/getGlossary")
    public Map<String, String> getGlossary() {
        return TranslateDataService.glossaryCache;
    }

    // 存glossary 缓存
    @GetMapping("/setGlossary")
    public void setGlossary(@RequestParam String shopName, @RequestParam String sourceText, @RequestParam String target, @RequestParam String targetText) {
        TranslateDataService.glossaryCache.put(TranslateDataService.generateGlossaryKey(shopName, target, sourceText), targetText);
    }

    // 获取glossary 缓存
    @GetMapping("/getGlossaryCache")
    public String getGlossaryCache(@RequestParam String shopName, @RequestParam String sourceText, @RequestParam String target) {
        return TranslateDataService.glossaryCache.get(TranslateDataService.generateGlossaryKey(shopName, target, sourceText));
    }

    /**
     * 测试删除shopify数据方法
     */
    @GetMapping("/testDeleteShopifyData")
    public String testDeleteShopifyData(@RequestParam String resourceId, @RequestParam String locals, @RequestParam String translationKeys, @RequestParam String accessToken) {
        return ShopifyHttpIntegration.deleteTranslateData("ciwishop.myshopify.com", accessToken, resourceId, locals, translationKeys);
    }

}
