package com.bogda.service.logic;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bogda.service.Service.IGlossaryService;
import com.bogda.service.Service.ITranslatesService;
import com.bogda.service.Service.IUsersService;
import com.bogda.service.Service.IWidgetConfigurationsService;
import com.bogda.common.entity.DO.GlossaryDO;
import com.bogda.common.entity.DO.TranslatesDO;
import com.bogda.common.entity.DO.UsersDO;
import com.bogda.common.entity.DO.WidgetConfigurationsDO;
import com.bogda.common.contants.TranslateConstants;
import com.bogda.common.utils.ShopifyRequestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static com.bogda.common.utils.ModuleCodeUtils.getLanguageName;

@Service
public class DataRatingService {
    @Autowired
    private IGlossaryService iGlossaryService;
    @Autowired
    private IWidgetConfigurationsService iWidgetConfigurationsService;
    @Autowired
    private ITranslatesService iTranslatesService;
    @Autowired
    private IUsersService iUsersService;
    @Autowired
    private ShopifyService shopifyService;

    /**
     * 术语表， switch表，  自动翻译
     * 查询这三个表是否开启
     */
    public Map<String, Boolean> queryDBConfiguration(String shopName) {
        Map<String, Boolean> configurationMap = new HashMap<>();
        //1, 查询术语表
        GlossaryDO[] glossaryByShopName = iGlossaryService.getGlossaryByShopName(shopName);
        if (glossaryByShopName.length > 0) {
            configurationMap.put("glossary", true);
        } else {
            configurationMap.put("glossary", false);
        }

        //2，查询switch表
        WidgetConfigurationsDO data = iWidgetConfigurationsService.getData(shopName);
        if (data != null) {
            configurationMap.put("switch", data.getLanguageSelector());
        }else {
            configurationMap.put("switch", false);
        }

        //3，查询自动翻译表
        List<TranslatesDO> one = iTranslatesService.list(new LambdaQueryWrapper<TranslatesDO>().eq(TranslatesDO::getShopName, shopName).eq(TranslatesDO::getAutoTranslate, 1));
        if (one != null && !one.isEmpty()) {
            configurationMap.put("autoTranslate", true);
        } else {
            configurationMap.put("autoTranslate", false);
        }

        return configurationMap;
    }

    /**
     * 获取用户商店语言开启状态
     */
    public Map<String, Integer> getTranslationStatus(String shopName, String source) {
        // 1， 从db获取用户token和相关翻译数据， 存入Map中
        Map<String, Integer> statusMap = new HashMap<>();
        UsersDO usersDO = iUsersService.getOne(new LambdaQueryWrapper<UsersDO>().eq(UsersDO::getShopName, shopName));
        List<TranslatesDO> list = iTranslatesService.list(new LambdaQueryWrapper<TranslatesDO>().eq(TranslatesDO::getShopName, shopName).eq(TranslatesDO::getSource, source));

        // 2，从shopify中获取所有的语言状态数据
        String shopifyByQuery = shopifyService.getShopifyData(shopName, usersDO.getAccessToken(), TranslateConstants.API_VERSION_LAST, ShopifyRequestUtils.getShopLanguageQuery());
        if (shopifyByQuery == null) {
            return null;
        }

        // 3，对shopify返回的数据进行解析，判断用户是否所有翻译语言都发布
        // 默认是全部发布了
        statusMap.put(TranslateConstants.IS_PUBLISH, 1);

        // 先转成 JSONObject
        JSONObject jsonObject = JSON.parseObject(shopifyByQuery);
        // 取出 shopLocales 数组
        JSONArray shopLocalesArr = jsonObject.getJSONArray("shopLocales");
        if (shopLocalesArr == null) {
            return null;
        }
        for (int i = 0; i < shopLocalesArr.size(); i++) {
            JSONObject item = shopLocalesArr.getJSONObject(i);
            String locale = item.getString("locale");
            Boolean published = item.getBoolean("published");
            String name = item.getString("name");
            //做处理只返回shopify里面有的数据
            statusMap.put(name, 0);
            if (Boolean.FALSE.equals(published) && statusMap.get(getLanguageName(locale)) >= 1) {
                //翻译后的语言没有发布
                statusMap.put(TranslateConstants.IS_PUBLISH, 0);
            }
        }

        // 4，从db中获取用户翻译状态，如果shopify里面没有，则不显示
        for (TranslatesDO translatesDO : list
        ) {
            if (statusMap.containsKey(getLanguageName(translatesDO.getTarget()))) {
                statusMap.put(getLanguageName(translatesDO.getTarget()), translatesDO.getStatus());
            }
        }

        // 5, 将source对应的语言删除
        statusMap.remove(getLanguageName(source));
        return statusMap;
    }

    /**
     * 计算评分， 目前的占比：语言是否翻译60%， 功能40%
     */
    public Double getRatingInfo(String shopName, String source) {
        final double CONFIG_WEIGHT = 0.4;
        final double STATUS_WEIGHT = 0.6;

        // 1. 查询商店配置
        Map<String, Boolean> configMap = queryDBConfiguration(shopName);
        if (configMap == null || configMap.isEmpty()) {
            return null;
        }

        long validConfigs = configMap.values().stream()
                .filter(Boolean::booleanValue)
                .count();
        double configScore = ((double) validConfigs / configMap.size()) * CONFIG_WEIGHT;

        // 2. 查询翻译状态
        Map<String, Integer> statusMap = getTranslationStatus(shopName, source);
        if (statusMap == null || statusMap.isEmpty()) {
            return null;
        }

        long passedStatus = statusMap.values().stream()
                .filter(v -> v == 1)
                .count();
        double statusScore = 0d;
        if (passedStatus > 0 && statusMap.size() > 1) {
            statusScore = ((double) (passedStatus - 1) / (statusMap.size() - 1)) * STATUS_WEIGHT;
        }

        // 3. 总分
        return configScore + statusScore;
    }
}
