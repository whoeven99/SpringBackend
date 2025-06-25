package com.bogdatech.controller;


import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bogdatech.Service.ITranslateTasksService;
import com.bogdatech.Service.impl.TranslatesServiceImpl;
import com.bogdatech.entity.DO.TestTableDO;
import com.bogdatech.entity.DO.TranslateTasksDO;
import com.bogdatech.entity.DO.TranslatesDO;
import com.bogdatech.entity.DTO.KeyValueDTO;
import com.bogdatech.entity.VO.RabbitMqTranslateVO;
import com.bogdatech.entity.VO.ChatgptVO;
import com.bogdatech.integration.ChatGptIntegration;
import com.bogdatech.integration.RateHttpIntegration;
import com.bogdatech.logic.*;
import com.bogdatech.mapper.TestTableMapper;
import com.bogdatech.model.controller.request.ClickTranslateRequest;
import com.bogdatech.model.controller.request.CloudServiceRequest;
import com.bogdatech.model.controller.request.ShopifyRequest;
import com.bogdatech.model.service.RabbitMqTranslateConsumerService;
import com.bogdatech.task.RabbitMqTask;
import com.bogdatech.utils.CharacterCountUtils;
import com.microsoft.applicationinsights.TelemetryClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.bogdatech.entity.DO.TranslateResourceDTO.TOKEN_MAP;
import static com.bogdatech.integration.RateHttpIntegration.rateMap;
import static com.bogdatech.integration.ShopifyHttpIntegration.getInfoByShopify;
import static com.bogdatech.logic.TranslateService.*;
import static com.bogdatech.task.RabbitMqTask.SHOP_LOCKS;
import static com.bogdatech.task.RabbitMqTask.unlock;
import static com.bogdatech.utils.ApiCodeUtils.getLanguageName;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.JsonUtils.isJson;
import static com.bogdatech.utils.JsoupUtils.isHtml;
import static com.bogdatech.utils.JudgeTranslateUtils.*;
import static com.bogdatech.utils.PlaceholderUtils.getSimplePrompt;
import static com.bogdatech.utils.StringUtils.replaceHyphensWithSpaces;

@RestController
public class TestController {
    private final TranslatesServiceImpl translatesServiceImpl;
    private final ChatGptIntegration chatGptIntegration;
    private final TestService testService;
    private final TaskService taskService;
    private final RateHttpIntegration rateHttpIntegration;
    private final RabbitMqTranslateService rabbitMqTranslateService;
    private final UserTypeTokenService userTypeTokenService;
    private final RabbitMqTranslateConsumerService rabbitMqTranslateConsumerService;
    private final TencentEmailService tencentEmailService;
    private final ITranslateTasksService translateTasksService;
    private final TestTableMapper testTableMapper;
    private final RabbitMqTask rabbitMqTask;

    @Autowired
    public TestController(TranslatesServiceImpl translatesServiceImpl, ChatGptIntegration chatGptIntegration, TestService testService, TaskService taskService, RateHttpIntegration rateHttpIntegration, RabbitMqTranslateService rabbitMqTranslateService, UserTypeTokenService userTypeTokenService, RabbitMqTranslateConsumerService rabbitMqTranslateConsumerService, TencentEmailService tencentEmailService, ITranslateTasksService translateTasksService, TestTableMapper testTableMapper, RabbitMqTask rabbitMqTask) {
        this.translatesServiceImpl = translatesServiceImpl;
        this.chatGptIntegration = chatGptIntegration;
        this.testService = testService;
        this.taskService = taskService;
        this.rateHttpIntegration = rateHttpIntegration;
        this.rabbitMqTranslateService = rabbitMqTranslateService;
        this.userTypeTokenService = userTypeTokenService;
        this.rabbitMqTranslateConsumerService = rabbitMqTranslateConsumerService;
        this.tencentEmailService = tencentEmailService;
        this.translateTasksService = translateTasksService;
        this.testTableMapper = testTableMapper;
        this.rabbitMqTask = rabbitMqTask;
    }

    @GetMapping("/ping")
    public String ping() {
        TelemetryClient appInsights = new TelemetryClient();
        appInsights.trackTrace("SpringBackend Ping Successful");
        return "Ping Successful!";
    }

    @GetMapping("/gpt")
    public ChatgptVO chat(@RequestParam String prompt, @RequestParam String sourceText) {
        return chatGptIntegration.chatWithGpt(prompt, sourceText);
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

    @GetMapping("/clickTranslate")
    public void clickTranslate() {
        testService.startTask();
    }

    @GetMapping("/stop")
    public void stop() {
        testService.stopTask();
    }


    //发送成功翻译的邮件gei
    @GetMapping("/sendEmail")
    public void sendEmail() {
        CharacterCountUtils characterCount = new CharacterCountUtils();
        characterCount.addChars(100);
        LocalDateTime localDateTime = LocalDateTime.now();
        tencentEmailService.translateFailEmail("ciwishop.myshopify.com", characterCount, localDateTime, 0, 1000, "zh-CN", "en");
    }

    //获取汇率
    @GetMapping("/getRate")
    public void getRate() {
        rateHttpIntegration.getFixerRate();
        appInsights.trackTrace("rateMap: " + rateMap.toString());
    }

    //测试缓存功能
    @GetMapping("/testCache")
    public String testCache() {
        return SINGLE_LINE_TEXT.toString();
    }

    @GetMapping("/testAddCache")
    public void testAddCache(String target, String value, String targetText) {
        addData(target, value, targetText);
    }


    @GetMapping("/testHtml")
    public void testHtml(@RequestParam String html) {
//        String html = """
//                啊手动阀手动阀
//                """;
        if (isHtml(html)) {
            System.out.println("is html");
        } else {
            System.out.println("is not html");
        }

        if (isJson(html)) {
            System.out.println("is json");
        } else {
            System.out.println("is not json");
        }
        String targetLanguage = getLanguageName("zh-CN");
        String prompt = getSimplePrompt(targetLanguage,  html);
        System.out.println("prompt: " + prompt);
//        String s = hunYuanTranslate(html, prompt, new CharacterCountUtils(), "zh-CN", HUN_YUAN_MODEL);
//        System.out.println("final: " + s);
//        String s = translateNewHtml(html, new TranslateRequest(0, "shop", "token", "en", "zh-CN", ""), new CharacterCountUtils(), "en");
//        System.out.println("final: " + normalizeHtml(html));
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
        if (!TRANSLATABLE_KEY_PATTERN.matcher(key).matches()) {
            return "theme不翻译";
        }
        //如果包含对应key和value，则跳过
        if (!shouldTranslate(key, value)) {
            return "theme不翻译";
        }
        return "需要翻译";
    }

    //测试自动翻译功能
    @PutMapping("/testAutoTranslate")
    public void testAutoTranslate() {
        taskService.autoTranslate();
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
        System.out.println("统计结束！！！");
    }

    @GetMapping("/testHandle")
    public String testHandle(@RequestParam String value) {
        return replaceHyphensWithSpaces(value);
    }

    @GetMapping("/testThread")
    public void logThreadPoolStatus() {
        if (executorService instanceof ThreadPoolExecutor executor) {
            appInsights.trackTrace(" - 锁池： " + SHOP_LOCKS);
            appInsights.trackTrace(" - 停止标志： " + userStopFlags);
            appInsights.trackTrace(" - 线程池状态：");
            appInsights.trackTrace(" - 核心线程数(corePoolSize): " + executor.getCorePoolSize());
            appInsights.trackTrace(" - 最大线程数(maximumPoolSize): " + executor.getMaximumPoolSize());
            appInsights.trackTrace(" - 当前线程池中线程数(poolSize): " + executor.getPoolSize());
            appInsights.trackTrace(" - 活跃线程数(activeCount): " + executor.getActiveCount());
            appInsights.trackTrace(" - 已完成任务数(completedTaskCount): " + executor.getCompletedTaskCount());
            appInsights.trackTrace(" - 总任务数(taskCount): " + executor.getTaskCount());
            appInsights.trackTrace(" - 当前排队任务数(queue size): " + executor.getQueue().size());
            appInsights.trackTrace(" - 队列剩余容量(remaining capacity): " + executor.getQueue().remainingCapacity());
            appInsights.trackTrace(" - 是否允许核心线程超时(allowsCoreThreadTimeOut): " + executor.allowsCoreThreadTimeOut());
            appInsights.trackTrace(" - 线程空闲存活时间(keepAliveTime): " + executor.getKeepAliveTime(TimeUnit.SECONDS) + " 秒");
            appInsights.trackTrace(" - 是否已关闭(isShutdown): " + executor.isShutdown());
            appInsights.trackTrace(" - 是否终止(isTerminated): " + executor.isTerminated());
            appInsights.trackTrace(" - 正在终止中(isTerminating): " + executor.isTerminating());
        }
    }

    @PostMapping("/testMqTranslate")
    public void testMqTranslate(@RequestBody ClickTranslateRequest clickTranslateRequest) {
//        rabbitMqTranslateConsumerService.handleUserRequest(clickTranslateRequest.getShopName());
        userEmailStatus.put(clickTranslateRequest.getShopName(), new AtomicBoolean(false)); //重置用户发送的邮件
        userStopFlags.put(clickTranslateRequest.getShopName(), new AtomicBoolean(false));  // 初始化用户的停止标志
        rabbitMqTranslateService.mqTranslate(clickTranslateRequest);

    }

    // 停止mq翻译任务
    @GetMapping("/stopMqTask")
    public void stopMqTask(@RequestParam String shopName) {
        appInsights.trackTrace("正在翻译的用户： " + userStopFlags);
        AtomicBoolean stopFlag = userStopFlags.get(shopName);
        if (stopFlag != null) {
            stopFlag.set(true);  // 设置停止标志，任务会在合适的地方检查并终止
            appInsights.trackTrace("停止成功");
        }
    }

    /**
     * 拆分一个大任务为几个小任务，把小任务的数据放到db里的TranslateTasks里面
     * */
    @PostMapping("/testDBTranslate1")
    public void testDBTranslate1(@RequestBody ClickTranslateRequest clickTranslateRequest) {
        TestTableDO name = testTableMapper.selectOne(new QueryWrapper<TestTableDO>().eq("name", clickTranslateRequest.getShopName()));
        if (name == null) {
            throw new RuntimeException("shopName不存在");
        }
        userEmailStatus.put(clickTranslateRequest.getShopName(), new AtomicBoolean(false)); //重置用户发送的邮件
        userStopFlags.put(clickTranslateRequest.getShopName(), new AtomicBoolean(false));  // 初始化用户的停止标志
        rabbitMqTranslateService.mqTranslate(clickTranslateRequest);

    }

    /**
     * 输入任务id，实现该任务的翻译
     * */
    @GetMapping("/testDBTranslate2")
    public void testDBTranslate2(@RequestParam String taskId) {
        //根据id获取数据，转化为规定数据类型
        RabbitMqTranslateVO dataToProcess = translateTasksService.getDataToProcess(taskId);
        rabbitMqTranslateConsumerService.processMessage(dataToProcess, new TranslateTasksDO());
    }

    /**
     * 修改用户锁集合
     * */
    @GetMapping("/testModifyLock")
    public String testModifyLock(@RequestParam String shopName) {
        unlock(shopName);
        return SHOP_LOCKS.toString();
    }

    /**
     * 启动DB翻译
     * */
    @GetMapping("/testDBTranslate")
    public void testDBTranslate() {
        rabbitMqTask.scanAndSubmitTasks();
    }

    /**
     * 将停止flag换成true
     * */
    @GetMapping("/testStopFlagToTure")
    public String testStopFlagToTure(@RequestParam String shopName) {
        userStopFlags.put(shopName, new AtomicBoolean(false));
        return userStopFlags.toString();
    }
}
