package com.bogdatech.controller;


import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.bogdatech.Service.ITranslateTasksService;
import com.bogdatech.Service.impl.TranslatesServiceImpl;
import com.bogdatech.entity.DO.TranslateResourceDTO;
import com.bogdatech.entity.DO.TranslateTasksDO;
import com.bogdatech.entity.DO.TranslatesDO;
import com.bogdatech.entity.DTO.FullAttributeSnapshotDTO;
import com.bogdatech.entity.DTO.KeyValueDTO;
import com.bogdatech.entity.VO.GptVO;
import com.bogdatech.entity.VO.RabbitMqTranslateVO;
import com.bogdatech.integration.ChatGptIntegration;
import com.bogdatech.integration.DeepLIntegration;
import com.bogdatech.integration.RateHttpIntegration;
import com.bogdatech.logic.*;
import com.bogdatech.model.controller.request.CloudServiceRequest;
import com.bogdatech.model.controller.request.ShopifyRequest;
import com.bogdatech.model.controller.request.TranslateRequest;
import com.bogdatech.model.service.RabbitMqTranslateConsumerService;
import com.bogdatech.task.RabbitMqTask;
import com.bogdatech.utils.CharacterCountUtils;
import com.bogdatech.utils.LiquidHtmlTranslatorUtils;
import com.microsoft.applicationinsights.TelemetryClient;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import static com.bogdatech.entity.DO.TranslateResourceDTO.TOKEN_MAP;
import static com.bogdatech.integration.RateHttpIntegration.rateMap;
import static com.bogdatech.integration.ShopifyHttpIntegration.getInfoByShopify;
import static com.bogdatech.logic.TranslateService.*;
import static com.bogdatech.task.RabbitMqTask.*;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.JudgeTranslateUtils.*;
import static com.bogdatech.utils.LiquidHtmlTranslatorUtils.HTML_TAG_PATTERN;
import static com.bogdatech.utils.MapUtils.getTranslationStatusMap;
import static com.bogdatech.utils.StringUtils.replaceHyphensWithSpaces;

@RestController
public class TestController {
    private final TranslatesServiceImpl translatesServiceImpl;
    private final ChatGptIntegration chatGptIntegration;
    private final TestService testService;
    private final TaskService taskService;
    private final RateHttpIntegration rateHttpIntegration;
    private final UserTypeTokenService userTypeTokenService;
    private final RabbitMqTranslateConsumerService rabbitMqTranslateConsumerService;
    private final TencentEmailService tencentEmailService;
    private final ITranslateTasksService translateTasksService;
    private final RabbitMqTask rabbitMqTask;
    private final LiquidHtmlTranslatorUtils liquidHtmlTranslatorUtils;

    @Autowired
    public TestController(TranslatesServiceImpl translatesServiceImpl, ChatGptIntegration chatGptIntegration, TestService testService, TaskService taskService, RateHttpIntegration rateHttpIntegration, UserTypeTokenService userTypeTokenService, RabbitMqTranslateConsumerService rabbitMqTranslateConsumerService, TencentEmailService tencentEmailService, ITranslateTasksService translateTasksService, RabbitMqTask rabbitMqTask, LiquidHtmlTranslatorUtils liquidHtmlTranslatorUtils) {
        this.translatesServiceImpl = translatesServiceImpl;
        this.chatGptIntegration = chatGptIntegration;
        this.testService = testService;
        this.taskService = taskService;
        this.rateHttpIntegration = rateHttpIntegration;
        this.userTypeTokenService = userTypeTokenService;
        this.rabbitMqTranslateConsumerService = rabbitMqTranslateConsumerService;
        this.tencentEmailService = tencentEmailService;
        this.translateTasksService = translateTasksService;
        this.rabbitMqTask = rabbitMqTask;
        this.liquidHtmlTranslatorUtils = liquidHtmlTranslatorUtils;
    }

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
        Timestamp now = new Timestamp(System.currentTimeMillis());
        tencentEmailService.sendAPGSuccessEmail("daoyee@ciwi.ai", 1L,"product","daoyee",now,9989, 10, 100001);
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
            if (whiteListTranslate(key)){
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

    @PutMapping("/testThread")
    public String logThreadPoolStatus() {
        if (executorService instanceof ThreadPoolExecutor executor) {
            String userTranslates = (" - 用户翻译Set： " + userTranslate + " ");
            String process = (" - 进程Set： " + PROCESSING_SHOPS + " ");
            String locks = (" - 锁池： " + SHOP_LOCKS + "  ");
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
            return userTranslates + process + locks + userStopFlag + executorServic + crePoolSize + maximumPoolSize + poolSize + activeCount + completedTaskCount + taskCount + queue + remainingCapacity + allowsCoreThreadTimeOut + keepAliveTime + shutdown + terminated + terminating;
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
        rabbitMqTranslateConsumerService.processMessage(dataToProcess, new TranslateTasksDO());
        translateTasksService.updateByTaskId(taskId, 1);
        unlock(dataToProcess.getShopName());
    }

    /**
     * 修改用户锁集合
     */

    @PutMapping("/testModifyLock")
    public String testModifyLock(@RequestParam String shopName) {
        ReentrantLock lock = SHOP_LOCKS.get(shopName);
        System.out.println("Trying to unlock " + shopName + " by thread " + Thread.currentThread().getName());
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
        } else {
            System.out.println("Unlock failed. Not held by current thread.");
        }
        return SHOP_LOCKS.toString();
    }

    /**
     * 启动DB翻译
     */
    @PutMapping("/testDBTranslate")
    public void testDBTranslate() {
        rabbitMqTask.scanAndSubmitTasks();
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
     * 在map里面清除锁，不建议且仅剩使用
     */
    @PutMapping("/testLock")
    public boolean testLock(@RequestParam String shopName) {
        SHOP_LOCKS.remove(shopName); // 强制移除 ReentrantLock 对象
        appInsights.trackTrace("SHOP_LOCKS: " + SHOP_LOCKS);
        return true;
    }

    /**
     * 对PROCESSING_SHOPS里面数据进行增删
     * ture,增； false，改
     */
    @PutMapping("/testProcess")
    public boolean testProcess(@RequestParam String shopName, @RequestParam Boolean flag) {
        if (flag) {
            return PROCESSING_SHOPS.add(shopName);
        }else {
            return PROCESSING_SHOPS.remove(shopName);
        }
    }

    /**
     * 一键式恢复用户翻译
     * */
    @PutMapping("/testRecover")
    public String testRecover(@RequestParam String shopName) {
        translateTasksService.update( new UpdateWrapper<TranslateTasksDO>().eq("shop_name", shopName).and(wrapper -> wrapper.eq("status", 2)).set("status", 4));
        SHOP_LOCKS.remove(shopName); // 强制移除 ReentrantLock 对象
        PROCESSING_SHOPS.remove(shopName);
        Map<String, Object> translationStatusMap = getTranslationStatusMap(" ", 1);
        userTranslate.put(shopName,translationStatusMap);
        return SHOP_LOCKS.toString() + PROCESSING_SHOPS;
    }


    /**
     * 对html翻译的新问题
     * */
    @GetMapping("/testHtml2")
    public String testHtml2() {
        String html = """
                
                
                """;
        boolean hasHtmlTag = HTML_TAG_PATTERN.matcher(html).find();
        Document originalDoc;
        if (hasHtmlTag) {
             originalDoc = Jsoup.parse(html);
        }else {
             originalDoc = Jsoup.parseBodyFragment(html);
        }

        // 2. 提取样式并标记 ID
        Map<String, FullAttributeSnapshotDTO> attrMap = liquidHtmlTranslatorUtils.tagElementsAndSaveFullAttributes(originalDoc);
        System.out.println("styleMap : " + attrMap);

        // 3. 获取清洗后的 HTML（无样式）
        liquidHtmlTranslatorUtils.removeAllAttributesExceptMarker(originalDoc);
        System.out.println("originalDoc : " + originalDoc);
        // 3. 翻译文本
//        translateTextNodes(doc.body(), text -> translateToChinese(text)); // 自定义翻译逻辑
        String cleanedHtml = originalDoc.body().html();
        System.out.println("cleanedHtml : " + cleanedHtml);
//        String targetLanguage = getLanguageName("zh-CN");
//        String fullHtmlPrompt = getFullHtmlPrompt(targetLanguage, "Home Goods");
//        System.out.println("fullHtmlPrompt : " + fullHtmlPrompt);
//        String s = chatGptIntegration.chatWithGpt(fullHtmlPrompt, cleanedHtml, new TranslateRequest(0, "ciwishop.myshopify.com", null, "en", "zh-CN", cleanedHtml), new CharacterCountUtils(), 5000000);
//        System.out.println("s : " + s);


        // 第四步：解析翻译后的 HTML
        Document translatedDoc;
        if (hasHtmlTag) {
//            translatedDoc = Jsoup.parse(s);
            translatedDoc = Jsoup.parse(cleanedHtml);
        }else {
//            translatedDoc = Jsoup.parseBodyFragment(s);
            translatedDoc = Jsoup.parseBodyFragment(cleanedHtml);
        }
        // 第五步：将样式还原到翻译后的 HTML 中
        liquidHtmlTranslatorUtils.restoreFullAttributes(translatedDoc, attrMap);
        System.out.println("doc 1 : " + translatedDoc.body().html());
        return translatedDoc.body().html();
    }
}
