package com.bogda.service.logic;

import com.bogda.common.reporter.ExceptionReporterHolder;
import com.bogda.common.reporter.TraceReporterHolder;
import com.bogda.service.Service.ITranslatesService;
import com.bogda.common.entity.DO.TranslatesDO;
import com.bogda.common.contants.TranslateConstants;
import com.bogda.common.utils.JsonUtils;
import com.bogda.common.utils.ShopifyRequestUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@EnableAsync
public class TranslateService {
    @Autowired
    private ITranslatesService translatesService;
    @Autowired
    private ShopifyService shopifyService;


    // 判断是否可以终止翻译流程
    public static ExecutorService executorService = Executors.newFixedThreadPool(50);

    /**
     * 手动停止用户的翻译任务（卸载应用时更新 Spring 侧翻译状态）。
     */
    public String stopTranslationManually(String shopName) {
        translatesService.updateStatusByShopNameAnd2(shopName);
        return "stopTranslationManually 翻译任务已停止 用户 " + shopName + " 的翻译任务已停止";
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
        String query = ShopifyRequestUtils.getLanguagesQuery();
        String shopifyData;
        JsonNode root;
        try {
            shopifyData = shopifyService.getShopifyData(shopName, accessToken, TranslateConstants.API_VERSION_LAST, query);
            root = JsonUtils.readTree(shopifyData);
        } catch (Exception e) {
            ExceptionReporterHolder.report("TranslateService.syncShopifyAndDatabase", e);
            TraceReporterHolder.report("TranslateService.syncShopifyAndDatabase", "FatalException syncShopifyAndDatabase Failed to get Shopify data errors : " + e.getMessage());
            return;
        }

        if (root == null) {
            return;
        }

        // 分析获取到的数据，然后存储到list集合里面
        JsonNode shopLocales = root.path("shopLocales");

        if (!shopLocales.isArray()) {
            TraceReporterHolder.report("TranslateService.syncShopifyAndDatabase", "syncShopifyAndDatabase: shopLocales is not an array.");
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
}

