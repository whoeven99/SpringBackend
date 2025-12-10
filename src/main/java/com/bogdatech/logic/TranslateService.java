package com.bogdatech.logic;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bogdatech.Service.ITranslateTasksService;
import com.bogdatech.Service.ITranslatesService;
import com.bogdatech.Service.ITranslationCounterService;
import com.bogdatech.Service.IUsersService;
import com.bogdatech.entity.DO.UsersDO;
import com.bogdatech.integration.ALiYunTranslateIntegration;
import com.bogdatech.integration.AidgeIntegration;
import com.bogdatech.logic.redis.TranslationParametersRedisService;
import com.bogdatech.model.controller.request.TranslateRequest;
import com.bogdatech.utils.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.bogdatech.constants.TranslateConstants.*;
import static com.bogdatech.integration.TranslateApiIntegration.getGoogleTranslationWithRetry;
import static com.bogdatech.requestBody.ShopifyRequestBody.getLanguagesQuery;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

@Component
@EnableAsync
public class TranslateService {
    @Autowired
    private ITranslatesService translatesService;
    @Autowired
    private ITranslateTasksService translateTasksService;
    @Autowired
    private IUsersService iUsersService;
    @Autowired
    private ITranslationCounterService iTranslationCounterService;
    @Autowired
    private TranslationParametersRedisService translationParametersRedisService;
    @Autowired
    private ShopifyService shopifyService;
    @Autowired
    private AidgeIntegration aidgeIntegration;

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // 判断是否可以终止翻译流程
    public static ConcurrentHashMap<String, Future<?>> userTasks = new ConcurrentHashMap<>(); // 存储每个用户的翻译任务

    // 使用 ConcurrentHashMap 存储每个用户的邮件发送状态
    public static ConcurrentHashMap<String, AtomicBoolean> userEmailStatus = new ConcurrentHashMap<>();
    public static ExecutorService executorService = Executors.newFixedThreadPool(50);

    /**
     * 手动停止用户的翻译任务
     * */
    public String stopTranslationManually(String shopName) {
//         v2, 后面的以后删掉
//        redisStoppedRepository.manuallyStopped(shopName);

        Boolean stopFlag = translationParametersRedisService.setStopTranslationKey(shopName);
        if (stopFlag) {
            appInsights.trackTrace("stopTranslationManually 用户 " + shopName + " 的翻译标识存储成功");

            //将Task表DB中的status改为5
            translateTasksService.updateStatusAllTo5ByShopName(shopName);

            // 将翻译状态改为“部分翻译” shopName, status=3
            translatesService.updateStatusByShopNameAnd2(shopName);

            Future<?> future = userTasks.get(shopName);

            // 目前能用到的就是私有key
            if (future != null && !future.isDone()) {
                // 中断正在执行的任务
                future.cancel(true);
            }

            return "stopTranslationManually 翻译任务已停止 用户 " + shopName + " 的翻译任务已停止";
        }
        return "已经有停止标识， 需要删除后再次停止";
    }

    //google翻译接口
    public String googleTranslate(TranslateRequest request) {
        return getGoogleTranslationWithRetry(request);
    }

    public static final Set<String> EXCLUDED_SHOPS = new HashSet<>(Arrays.asList(
            "qnxrrk-2n.myshopify.com",
            "gemxco.myshopify.com"
    ));

    public static final Set<String> PRODUCT_MODEL = new HashSet<>(Arrays.asList(
            PRODUCT_OPTION,
            PRODUCT_OPTION_VALUE
    ));

    public static Map<String, Object> createTranslationMap(String target, String key, String translatableContentDigest) {
        Map<String, Object> translation = new HashMap<>();
        translation.put("locale", target);
        translation.put("key", key);
        translation.put("translatableContentDigest", translatableContentDigest);
        return translation;
    }

    /**
     * 根据店铺名称和target获取对应的 ID。
     */
    public int getIdByShopNameAndTargetAndSource(String shopName, String target, String source) {
        // 调用 translatesService 的 getIdByShopNameAndTarget 方法，传入店铺名称和目标值
        // 该方法负责实际的逻辑处理（如数据库查询），并返回对应的 ID
        return translatesService.getIdByShopNameAndTargetAndSource(shopName, target, source);
    }

    /**
     * 用户shopify和数据库同步的方法
     */
    public void syncShopifyAndDatabase(String shopName, String accessToken, String source) {
        String query = getLanguagesQuery();
        String shopifyData;
        JsonNode root;
        try {
            shopifyData = shopifyService.getShopifyData(shopName, accessToken, API_VERSION_LAST, query);
            root = JsonUtils.readTree(shopifyData);
        } catch (Exception e) {
            appInsights.trackException(e);
            appInsights.trackTrace("syncShopifyAndDatabase Failed to get Shopify data errors : " + e.getMessage());
            return;
        }

        if (root == null) {
            return;
        }

        // 分析获取到的数据，然后存储到list集合里面
        JsonNode shopLocales = root.path("shopLocales");
        if (shopLocales.isArray()) {
            for (JsonNode node : shopLocales) {
                String locale = node.path("locale").asText(null);
                if (locale != null) {
                    //存储到数据库中
                    translatesService.insertShopTranslateInfoByShopify(shopName, accessToken, locale, source);
                }
            }
        }
    }

    public String imageTranslate(String sourceCode, String targetCode, String imageUrl, String shopName, String accessToken) {
        appInsights.trackTrace("imageTranslate 用户 " + shopName + " sourceCode " + sourceCode + " targetCode " + targetCode + " imageUrl " + imageUrl + " accessToken " + accessToken);

        // 获取用户token，判断是否和数据库中一致再选择是否调用
        UsersDO usersDO = iUsersService.getOne(new LambdaQueryWrapper<UsersDO>().eq(UsersDO::getShopName, shopName));
        if (!usersDO.getAccessToken().equals(accessToken)) {
            return null;
        }

        // 获取用户最大额度限制
        Integer maxCharsByShopName = iTranslationCounterService.getMaxCharsByShopName(shopName);

        // 调用图片翻译方法
        return aidgeIntegration.aidgeStandPictureTranslate(shopName, imageUrl, sourceCode, targetCode, maxCharsByShopName, ALiYunTranslateIntegration.TRANSLATE_APP);
    }
}

