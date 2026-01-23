package com.bogda.api;

import com.alibaba.fastjson.JSONObject;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.models.SecretProperties;
import com.bogda.common.entity.DO.UsersDO;
import com.bogda.integration.aimodel.RateHttpIntegration;
import com.bogda.service.Service.ITranslatesService;
import com.bogda.common.entity.DO.TranslatesDO;
import com.bogda.common.entity.VO.UserDataReportVO;
import com.bogda.integration.aimodel.GoogleMachineIntegration;
import com.bogda.integration.shopify.ShopifyHttpIntegration;
import com.bogda.service.Service.IUsersService;
import com.bogda.service.logic.RedisDataReportService;
import com.bogda.service.logic.RedisProcessService;
import com.bogda.service.logic.redis.RateRedisService;
import com.bogda.service.logic.translate.TranslateV2Service;
import com.bogda.common.controller.request.CloudServiceRequest;
import com.bogda.common.controller.response.BaseResponse;
import com.bogda.common.utils.AppInsightsUtils;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@RestController
public class TestController {
    @Value("${test.keyvault:default-vault}")
    private String testKeyvault;

    @Autowired
    private RedisProcessService redisProcessService;
    @Autowired
    private RedisDataReportService redisDataReportService;
    @Autowired
    private TranslateV2Service translateV2Service;
    @Autowired
    private ITranslatesService iTranslatesService;
    @Autowired
    private GoogleMachineIntegration googleMachineIntegration;
    @Autowired
    private ShopifyHttpIntegration shopifyHttpIntegration;
    @Autowired
    private RateHttpIntegration rateHttpIntegration;
    @Autowired
    private RateRedisService rateRedisService;
    @Autowired
    private IUsersService iUsersService;

    // 由 spring-cloud-azure-starter-keyvault-secrets 自动创建，使用 bootstrap.yml 中的配置
    @Autowired
    private SecretClient secretClient;

    @GetMapping("/test")
    public String test() {
        // 从 Key Vault 或 application 配置中读取 test.keyvault
        return testKeyvault;
    }

    /**
     * 使用 Spring Environment 枚举通过 bootstrap.yml 加载的 Key Vault 属性
     * 方便验证 spring-cloud-azure-starter-keyvault-secrets 是否把 secrets 映射成了配置属性
     */
    @Autowired
    private Environment environment;

    @GetMapping("/testKeyVaultProps")
    public Map<String, Object> testKeyVaultProps() {
        Map<String, Object> result = new HashMap<>();
        if (!(environment instanceof ConfigurableEnvironment configurableEnvironment)) {
            result.put("error", "Environment 不是 ConfigurableEnvironment，无法枚举属性");
            return result;
        }

        for (PropertySource<?> propertySource : configurableEnvironment.getPropertySources()) {
            // 一般 Key Vault 的 PropertySource 名称里会包含 "keyvault" 或 "azure"
            boolean maybeKeyVaultSource = propertySource.getName().toLowerCase().contains("keyvault")
                    || propertySource.getName().toLowerCase().contains("azure");

            if (maybeKeyVaultSource && propertySource instanceof EnumerablePropertySource<?> enumerablePropertySource) {
                for (String name : enumerablePropertySource.getPropertyNames()) {
                    result.put(name, enumerablePropertySource.getProperty(name));
                }
            }
        }

        if (result.isEmpty()) {
            result.put("info", "没有在 PropertySources 中发现带 keyvault/azure 的可枚举属性源，可能 bootstrap 未把 Key Vault secrets 注入为属性");
        }

        return result;
    }

    /**
     * 简单判断 Key Vault 是否连通：列出所有 secret 名称
     */
    @GetMapping("/testKeyVaultSecrets")
    public List<String> testKeyVaultSecrets() {
        List<String> names = new ArrayList<>();
        if (secretClient == null) {
            names.add("SecretClient is null - Key Vault 未正确配置或 starter 未生效");
            return names;
        }
        for (SecretProperties properties : secretClient.listPropertiesOfSecrets()) {
            names.add(properties.getName());
        }
        return names;
    }

    @GetMapping("/ping")
    public String ping() {
        AppInsightsUtils.trackTrace("SpringBackend Ping Successful");
        return "Ping Successful!";
    }

    // 通过测试环境调shopify的API
    @PostMapping("/test123")
    public String test(@RequestBody CloudServiceRequest cloudServiceRequest) {
        String body = cloudServiceRequest.getBody();
        JSONObject infoByShopify = shopifyHttpIntegration.getInfoByShopify(cloudServiceRequest.getShopName(), cloudServiceRequest.getAccessToken(), body);
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

    /**
     * 单纯的打印信息
     */
    @PostMapping("/frontEndPrinting")
    public void frontEndPrinting(@RequestBody String data) {
        AppInsightsUtils.trackTrace(data);
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

    @GetMapping("/testAutoEmail")
    public void testAutoEmail(@RequestParam String shopName) {
        List<TranslatesDO> translatesDOList = iTranslatesService.listAutoTranslates(shopName);
        for (TranslatesDO translatesDO : translatesDOList) {
            translateV2Service.testAutoTranslate(shopName, translatesDO.getSource(), translatesDO.getTarget());
        }
    }

    // 手动获取rate
    @GetMapping("/testRate")
    public void testRate() {
        var rates = rateHttpIntegration.getFixerRate();
        rateRedisService.refreshRates(rates);
    }


    private PerformanceResult testHttpClient5(String url, String query, Map<String, String> headers,
                                              int warmupIterations, int testIterations, int concurrentThreads) throws Exception {
        HttpClient5Integration client = new HttpClient5Integration();
        try {
            // 预热
            for (int i = 0; i < warmupIterations; i++) {
                try {
                    client.httpPost(url, query, headers);
                } catch (Exception e) {
                    // 忽略预热错误
                }
            }

            // 单线程测试
            List<Long> singleThreadTimes = new ArrayList<>();
            for (int i = 0; i < testIterations; i++) {
                long startTime = System.currentTimeMillis();
                client.httpPost(url, query, headers);
                long endTime = System.currentTimeMillis();
                singleThreadTimes.add(endTime - startTime);
            }

            // 并发测试
            List<Long> concurrentTimes = testConcurrent5(url, query, headers, testIterations, concurrentThreads);

            return new PerformanceResult("HttpClient5", singleThreadTimes, concurrentTimes);
        } finally {
            client.close();
        }
    }

    private List<Long> testConcurrent5(String url, String query, Map<String, String> headers,
                                       int testIterations, int concurrentThreads) {
        ExecutorService executor = Executors.newFixedThreadPool(concurrentThreads);
        List<CompletableFuture<Long>> futures = new ArrayList<>();

        for (int i = 0; i < testIterations; i++) {
            CompletableFuture<Long> future = CompletableFuture.supplyAsync(() -> {
                HttpClient5Integration threadClient = new HttpClient5Integration();
                try {
                    long startTime = System.currentTimeMillis();
                    threadClient.httpPost(url, query, headers);
                    long endTime = System.currentTimeMillis();
                    return endTime - startTime;
                } catch (Exception e) {
                    return 0L;
                } finally {
                    try {
                        threadClient.close();
                    } catch (IOException e) {
                        // 忽略关闭错误
                    }
                }
            }, executor);
            futures.add(future);
        }

        List<Long> times = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

        executor.shutdown();
        return times;
    }

    // HttpClient 4.5.6 实现
    static class HttpClient4Integration {
        private final CloseableHttpClient httpClient;

        public HttpClient4Integration() {
            this.httpClient = HttpClients.createDefault();
        }

        public String httpPost(String url, String body, Map<String, String> headers) {
            HttpPost http = new HttpPost(url);
            try {
                http.setEntity(new StringEntity(body, "UTF-8"));
            } catch (Exception e) {
                return null;
            }
            return sendHttp(http, headers);
        }

        private String sendHttp(HttpRequestBase http, Map<String, String> headers) {
            http.addHeader("Content-Type", "application/json");
            for (Map.Entry<String, String> header : headers.entrySet()) {
                http.addHeader(header.getKey(), header.getValue());
            }

            try {
                CloseableHttpResponse response = httpClient.execute(http);
                if (response == null || response.getEntity() == null) {
                    return null;
                }
                String responseContent = EntityUtils.toString(response.getEntity(), "UTF-8");
                response.close();
                return responseContent;
            } catch (Exception e) {
                return null;
            }
        }

        public void close() throws IOException {
            httpClient.close();
        }
    }

    // HttpClient5 实现
    static class HttpClient5Integration {
        private final org.apache.hc.client5.http.impl.classic.CloseableHttpClient httpClient;

        public HttpClient5Integration() {
            this.httpClient = org.apache.hc.client5.http.impl.classic.HttpClients.createDefault();
        }

        public String httpPost(String url, String body, Map<String, String> headers) {
            org.apache.hc.client5.http.classic.methods.HttpPost http = new org.apache.hc.client5.http.classic.methods.HttpPost(url);
            http.setEntity(new org.apache.hc.core5.http.io.entity.StringEntity(body, ContentType.APPLICATION_JSON.withCharset("UTF-8")));
            return sendHttp(http, headers);
        }

        private String sendHttp(ClassicHttpRequest http, Map<String, String> headers) {
            http.addHeader("Content-Type", "application/json");
            for (Map.Entry<String, String> header : headers.entrySet()) {
                http.addHeader(header.getKey(), header.getValue());
            }

            try {
                return httpClient.execute(http, (org.apache.hc.core5.http.ClassicHttpResponse response) -> {
                    var entity = response.getEntity();
                    if (entity == null) {
                        return null;
                    }
                    String responseContent = org.apache.hc.core5.http.io.entity.EntityUtils.toString(entity);
                    org.apache.hc.core5.http.io.entity.EntityUtils.consume(entity);
                    return responseContent;
                });
            } catch (Exception e) {
                return null;
            }
        }

        public void close() throws IOException {
            httpClient.close();
        }
    }

    static class PerformanceResult {
        String name;
        double avgSingleThreadTime;
        double avgConcurrentTime;
        double throughputSingleThread;
        double throughputConcurrent;
        long minTime;
        long maxTime;
        long p50Time;
        long p95Time;
        long p99Time;

        PerformanceResult(String name, List<Long> singleThreadTimes, List<Long> concurrentTimes) {
            this.name = name;
            this.avgSingleThreadTime = calculateAverage(singleThreadTimes);
            this.avgConcurrentTime = calculateAverage(concurrentTimes);
            this.throughputSingleThread = 1000.0 / avgSingleThreadTime;
            this.throughputConcurrent = 1000.0 / avgConcurrentTime;
            this.minTime = singleThreadTimes.stream().mapToLong(Long::longValue).min().orElse(0);
            this.maxTime = singleThreadTimes.stream().mapToLong(Long::longValue).max().orElse(0);
            List<Long> sorted = singleThreadTimes.stream().sorted().collect(Collectors.toList());
            this.p50Time = getPercentile(sorted, 50);
            this.p95Time = getPercentile(sorted, 95);
            this.p99Time = getPercentile(sorted, 99);
        }

        private double calculateAverage(List<Long> times) {
            return times.stream().mapToLong(Long::longValue).average().orElse(0.0);
        }

        private long getPercentile(List<Long> sorted, int percentile) {
            int index = (int) Math.ceil(sorted.size() * percentile / 100.0) - 1;
            return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("name", name);
            map.put("avgSingleThreadTime", avgSingleThreadTime);
            map.put("avgConcurrentTime", avgConcurrentTime);
            map.put("throughputSingleThread", throughputSingleThread);
            map.put("throughputConcurrent", throughputConcurrent);
            map.put("minTime", minTime);
            map.put("maxTime", maxTime);
            map.put("p50Time", p50Time);
            map.put("p95Time", p95Time);
            map.put("p99Time", p99Time);
            return map;
        }
    }



    /**
     * HttpClient 性能对比测试
     * 对比 httpclient 4.5.6 和 httpclient5 对 Shopify GraphQL API 的调用性能
     */
    @GetMapping("/testHttpClientPerformance")
    public BaseResponse<Object> testHttpClientPerformance() {
        try {
            String shopifyUrl = "https://ciwishop.myshopify.com/admin/api/2025-10/graphql.json";
            UsersDO userByName = iUsersService.getUserByName("ciwishop.myshopify.com");
            String accessToken = userByName.getAccessToken();
            String graphqlQuery = "{\"query\":\"query GetSubscriptionDetails { node(id: \\\"gid://shopify/AppSubscription/26136412183\\\") { ... on AppSubscription { id name status createdAt currentPeriodEnd trialDays lineItems { plan { pricingDetails { __typename ... on AppRecurringPricing { price { amount currencyCode } } } } } } } }\"}";
            int warmupIterations = 5;
            int testIterations = 50;
            int concurrentThreads = 10;

            Map<String, String> headers = new HashMap<>();
            headers.put("X-Shopify-Access-Token", accessToken);

            // 测试 HttpClient5
            PerformanceResult result5 = testHttpClient5(shopifyUrl, graphqlQuery, headers, warmupIterations, testIterations, concurrentThreads);

            // 测试 HttpClient 4.5.6
            PerformanceResult result4 = testHttpClient4(shopifyUrl, graphqlQuery, headers, warmupIterations, testIterations, concurrentThreads);

            // 性能对比结果
            Map<String, Object> comparison = new HashMap<>();
            comparison.put("httpClient5", result5.toMap());
            comparison.put("httpClient4", result4.toMap());

            double improvement1 = ((result4.avgSingleThreadTime - result5.avgSingleThreadTime) / result4.avgSingleThreadTime) * 100;
            double improvement2 = ((result4.avgConcurrentTime - result5.avgConcurrentTime) / result4.avgConcurrentTime) * 100;
            double improvement3 = ((result5.throughputSingleThread - result4.throughputSingleThread) / result4.throughputSingleThread) * 100;
            double improvement4 = ((result5.throughputConcurrent - result4.throughputConcurrent) / result4.throughputConcurrent) * 100;

            Map<String, Double> improvements = new HashMap<>();
            improvements.put("单线程平均响应时间", improvement1);
            improvements.put("并发平均响应时间", improvement2);
            improvements.put("单线程吞吐量", improvement3);
            improvements.put("并发吞吐量", improvement4);
            comparison.put("improvements", improvements);

            return new BaseResponse<>().CreateSuccessResponse(comparison);
        } catch (Exception e) {
            AppInsightsUtils.trackException(e);
            return new BaseResponse<>().CreateErrorResponse("测试失败: " + e.getMessage());
        }
    }

    private PerformanceResult testHttpClient4(String url, String query, Map<String, String> headers,
                                              int warmupIterations, int testIterations, int concurrentThreads) throws Exception {
        HttpClient4Integration client = new HttpClient4Integration();
        try {
            // 预热
            for (int i = 0; i < warmupIterations; i++) {
                try {
                    client.httpPost(url, query, headers);
                } catch (Exception e) {
                    // 忽略预热错误
                }
            }

            // 单线程测试
            List<Long> singleThreadTimes = new ArrayList<>();
            for (int i = 0; i < testIterations; i++) {
                long startTime = System.currentTimeMillis();
                client.httpPost(url, query, headers);
                long endTime = System.currentTimeMillis();
                singleThreadTimes.add(endTime - startTime);
            }

            // 并发测试
            List<Long> concurrentTimes = testConcurrent4(url, query, headers, testIterations, concurrentThreads);

            return new PerformanceResult("HttpClient 4.5.6", singleThreadTimes, concurrentTimes);
        } finally {
            client.close();
        }
    }

    private List<Long> testConcurrent4(String url, String query, Map<String, String> headers,
                                       int testIterations, int concurrentThreads) {
        ExecutorService executor = Executors.newFixedThreadPool(concurrentThreads);
        List<CompletableFuture<Long>> futures = new ArrayList<>();

        for (int i = 0; i < testIterations; i++) {
            CompletableFuture<Long> future = CompletableFuture.supplyAsync(() -> {
                HttpClient4Integration threadClient = new HttpClient4Integration();
                try {
                    long startTime = System.currentTimeMillis();
                    threadClient.httpPost(url, query, headers);
                    long endTime = System.currentTimeMillis();
                    return endTime - startTime;
                } catch (Exception e) {
                    return 0L;
                } finally {
                    try {
                        threadClient.close();
                    } catch (IOException e) {
                        // 忽略关闭错误
                    }
                }
            }, executor);
            futures.add(future);
        }

        List<Long> times = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

        executor.shutdown();
        return times;
    }
}
