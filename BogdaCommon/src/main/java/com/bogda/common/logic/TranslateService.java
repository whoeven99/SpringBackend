package com.bogda.common.logic;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bogda.common.Service.ITranslatesService;
import com.bogda.common.Service.ITranslationCounterService;
import com.bogda.common.Service.IUsersService;
import com.bogda.common.entity.DO.TranslatesDO;
import com.bogda.common.entity.DO.UsersDO;
import com.bogda.common.integration.ALiYunTranslateIntegration;
import com.bogda.common.integration.AidgeIntegration;
import com.bogda.common.logic.redis.RedisStoppedRepository;
import com.bogda.common.model.controller.request.TranslateRequest;
import com.bogda.common.requestBody.ShopifyRequestBody;
import com.bogda.common.utils.JsonUtils;
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
import static com.bogda.common.constants.TranslateConstants.*;
import static com.bogda.common.integration.TranslateApiIntegration.getGoogleTranslationWithRetry;
import static com.bogda.common.utils.CaseSensitiveUtils.appInsights;

@Component
@EnableAsync
public class TranslateService {
    @Autowired
    private ITranslatesService translatesService;
    @Autowired
    private IUsersService iUsersService;
    @Autowired
    private ITranslationCounterService iTranslationCounterService;
    @Autowired
    private ShopifyService shopifyService;
    @Autowired
    private AidgeIntegration aidgeIntegration;
    @Autowired
    private RedisStoppedRepository redisStoppedRepository;

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // 判断是否可以终止翻译流程
    public static ConcurrentHashMap<String, Future<?>> userTasks = new ConcurrentHashMap<>(); // 存储每个用户的翻译任务
    public static ExecutorService executorService = Executors.newFixedThreadPool(50);

    /**
     * 手动停止用户的翻译任务
     */
    public String stopTranslationManually(String shopName) {
//         v2, 后面的以后删掉
        redisStoppedRepository.manuallyStopped(shopName);
        appInsights.trackTrace("stopTranslationManually 用户 " + shopName + " 的翻译标识存储成功");

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

    //google翻译接口
    public String googleTranslate(TranslateRequest request) {
        return getGoogleTranslationWithRetry(request);
    }

    /**
     * 根据店铺名称和target获取对应的 ID。
     *
     * <p>此方法通过调用 translatesService 服务层方法，根据传入的店铺名称和target查询并返回一个唯一的 ID。</p>
     *
     * @param shopName 店铺名称，用于标识特定的店铺，通常是一个非空的字符串
     * @param target   目标值，语言代码，通常是一个非空的字符串
     * @return int 返回与店铺名称和目标值匹配的 ID，如果未找到匹配记录，通常返回 null
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
        String query = ShopifyRequestBody.getLanguagesQuery();
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

        if (!shopLocales.isArray()) {
            appInsights.trackTrace("syncShopifyAndDatabase: shopLocales is not an array.");
            return;
        }

        // 获取db中用户，source下对应的所有target
        List<TranslatesDO> doList = translatesService.selectTargetByShopNameSource(shopName, source);
        List<String> targetList;
        if (doList == null || doList.isEmpty()) {
            targetList = new ArrayList<>();
        }else {
            targetList = doList.stream().map(TranslatesDO::getTarget).toList();
        }

        for (JsonNode node : shopLocales) {
            String locale = node.path("locale").asText(null);
            if (locale != null && !targetList.contains(locale)) {
                // 存储到数据库中
                translatesService.insertShopTranslateInfoByShopify(shopName, accessToken, locale, source);
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

