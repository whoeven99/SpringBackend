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
import com.bogdatech.integration.RateHttpIntegration;
import com.bogdatech.integration.ShopifyHttpIntegration;
import com.bogdatech.logic.*;
import com.bogdatech.logic.redis.TranslationCounterRedisService;
import com.bogdatech.logic.redis.TranslationMonitorRedisService;
import com.bogdatech.logic.redis.TranslationParametersRedisService;
import com.bogdatech.logic.translate.TranslateDataService;
import com.bogdatech.mapper.InitialTranslateTasksMapper;
import com.bogdatech.model.controller.request.CloudServiceRequest;
import com.bogdatech.model.controller.request.ShopifyRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.task.DBTask;
import com.bogdatech.task.IpEmailTask;
import com.bogdatech.utils.AESUtils;
import com.bogdatech.utils.TimeOutUtils;
import com.microsoft.applicationinsights.TelemetryClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import static com.bogdatech.entity.DO.TranslateResourceDTO.TOKEN_MAP;
import static com.bogdatech.integration.RateHttpIntegration.rateMap;
import static com.bogdatech.integration.ShopifyHttpIntegration.getInfoByShopify;
import static com.bogdatech.logic.TranslateService.*;
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
    private ITranslateTasksService translateTasksService;
    @Autowired
    private RedisProcessService redisProcessService;
    @Autowired
    private RedisDataReportService redisDataReportService;
    @Autowired
    private RedisTranslateLockService redisTranslateLockService;
    @Autowired
    private ITranslatesService translatesService;
    @Autowired
    private IpEmailTask ipEmailTask;

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

    // 通过测试环境调shopify的API
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

    //测试获取缓存功能
    @GetMapping("/testCache")
    public String testCache(@RequestParam String target, @RequestParam String value) {
        return redisProcessService.getCacheData(target, value);
    }

    @GetMapping("/testAddCache")
    public void testAddCache(String target, String value, String targetText) {
        redisProcessService.setCacheData(target, targetText, value);
    }

    //测试自动翻译功能
    @PutMapping("/testAutoTranslate")
    public void testAutoTranslate() {
        appInsights.trackTrace("testAutoTranslate 开始调用");
        executorService.execute(() -> taskService.autoTranslate());
    }

    // 暂时存在下
    @GetMapping("/testUnlock")
    public Boolean testUnlock(@RequestParam String shopName) {
        return redisTranslateLockService.setRemove(shopName);
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

    @Autowired
    private TranslationMonitorRedisService translationMonitorRedisService;
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

    @GetMapping("/testEmail")
    public void testEmail() {
        ipEmailTask.sendEmailTask();
    }
}
