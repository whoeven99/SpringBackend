package com.bogda.api;

import com.alibaba.fastjson.JSONObject;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.models.SecretProperties;
import com.bogda.integration.aimodel.AliyunSlsIntegration;
import com.bogda.integration.aimodel.RateHttpIntegration;
import com.bogda.service.Service.ITranslatesService;
import com.bogda.common.entity.DO.TranslatesDO;
import com.bogda.common.entity.VO.UserDataReportVO;
import com.bogda.integration.shopify.ShopifyHttpIntegration;
import com.bogda.service.logic.RedisDataReportService;
import com.bogda.service.logic.RedisProcessService;
import com.bogda.service.logic.redis.RateRedisService;
import com.bogda.service.logic.translate.TranslateV2Service;
import com.bogda.common.controller.request.CloudServiceRequest;
import com.bogda.common.controller.response.BaseResponse;
import com.bogda.common.utils.AppInsightsUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private ShopifyHttpIntegration shopifyHttpIntegration;
    @Autowired
    private RateHttpIntegration rateHttpIntegration;
    @Autowired
    private RateRedisService rateRedisService;
    @Autowired
    private AliyunSlsIntegration aliyunSlsIntegration;

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

    // 读数据
    @PostMapping("/testReadData")
    public List<Map<String, String>> testReadData() {
        return aliyunSlsIntegration.readAggLogs(1769409842, 1769415028, "event:product_viewed | select productId, count(*) as pv group by productId");
    }

    // 假数据
    @GetMapping("/gmv")
    public BaseResponse<Object> getGmvOverview() {
        String fakeData = """
                {
                  "totalGmv": 125430,
                  "gmvGrowthRate": 15.2,
                  "activeOffers": 24,
                  "newOffersThisWeek": 3,
                  "avgConversionRate": 2.8,
                  "conversionTrend": 0
                }
                """;

        return new BaseResponse<>().CreateSuccessResponse(fakeData);
    }

    @GetMapping("/ayt")
    public BaseResponse<Object> getAnalytics() {
        String fakeData = """
                {
                    "visitors": 33,
                    "bundleOrders": 0,
                    "conversionRate": 0.0,
                    "addedRevenue": 0.0,
                    "bundleConversion": {
                      "converted": 0,
                      "notConverted": 33
                    },
                    "dailyAddedRevenue": [
                      { "date": "2025-11-29", "amount": 2 },
                      { "date": "2025-12-02", "amount": 18 },
                      { "date": "2025-12-05", "amount": 463 },
                      { "date": "2025-12-08", "amount": 83 },
                      { "date": "2025-12-11", "amount": 36 },
                      { "date": "2025-12-14", "amount": 123 },
                      { "date": "2025-12-17", "amount": 567 },
                      { "date": "2025-12-20", "amount": 246 },
                      { "date": "2025-12-23", "amount": 74 },
                      { "date": "2025-12-26", "amount": 12 }
                    ]
                  }
                """;

        return new BaseResponse<>().CreateSuccessResponse(fakeData);
    }

    @GetMapping("/offers")
    public BaseResponse<Object> getOffers() {
        String fakeData = """
                {
                   "total": 1,
                   "list": [
                     {
                       "offerId": 1769049030510,
                       "offerName": "#Bundle 1769049030510",
                       "status": "ACTIVE",
                       "exposurePv": 120,
                       "addToCartPv": 18,
                       "gmv": 245.5,
                       "conversionRate": 15.0
                     }
                   ]
                 }
                """;

        return new BaseResponse<>().CreateSuccessResponse(fakeData);
    }
}
