package com.bogda.service.Service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.service.Service.ITranslationCounterService;
import com.bogda.service.Service.IUserIpService;
import com.bogda.common.entity.DO.TranslationCounterDO;
import com.bogda.service.logic.ShopifyService;
import com.bogda.service.logic.redis.OrdersRedisService;
import com.bogda.service.mapper.TranslationCounterMapper;
import com.bogda.common.controller.request.TranslationCounterRequest;
import com.bogda.common.contants.TranslateConstants;
import com.bogda.common.utils.AppInsightsUtils;
import com.bogda.common.utils.ShopifyRequestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import static com.bogda.common.utils.ShopifyUtils.isQueryValid;

@Service
@Transactional
public class TranslationCounterServiceImpl extends ServiceImpl<TranslationCounterMapper, TranslationCounterDO> implements ITranslationCounterService {
    @Autowired
    private OrdersRedisService ordersRedisService;
    @Autowired
    private ShopifyService shopifyService;
    @Autowired
    private IUserIpService iUserIpService;

    @Override
    public TranslationCounterDO readCharsByShopName(String shopName) {
        return baseMapper.readCharsByShopName(shopName);
    }

    @Override
    public int insertCharsByShopName(String shopName) {
        return baseMapper.insertCharsByShopName(shopName, 0);
    }

    public int updateUsedCharsByShopName(String shopName, Integer usedChars) {
        return baseMapper.updateUsedCharsByShopName(shopName, usedChars);
    }

    @Override
    public Integer getMaxCharsByShopName(String shopName) {
        return baseMapper.getMaxCharsByShopName(shopName);
    }

    @Override
    public Boolean updateCharsByShopName(String shopName, String accessToken, String gid, Integer chars) {
        // 添加订单标识
        ordersRedisService.setOrderId(shopName, gid);

        // 根据gid，判断是否符合添加额度的条件
        AppInsightsUtils.trackTrace("updateCharsByShopName 用户： " + shopName + " gid: " + gid + " chars: " + chars + " accessToken: " + accessToken);

        // 根据传来的gid获取， 判断调用那个方法，查询相关订阅信息
        String query;
        if (gid.contains("AppPurchaseOneTime")){
            AppInsightsUtils.trackTrace("一次性购买 用户： " + shopName);
            query = ShopifyRequestUtils.getSingleQuery(gid);
        }else {
            AppInsightsUtils.trackTrace("计划购买 用户： " + shopName);
            query = ShopifyRequestUtils.getSubscriptionQuery(gid);
        }
        AppInsightsUtils.trackTrace("updateCharsByShopName 用户： " + shopName + " query: " + query);

        String shopifyByQuery = shopifyService.getShopifyData(shopName, accessToken, TranslateConstants.API_VERSION_LAST, query);
        AppInsightsUtils.trackTrace("addCharsByShopNameAfterSubscribe " + shopName + " 用户 订阅信息 ：" + shopifyByQuery);


        // 判断和解析相关数据
        JSONObject queryValid = isQueryValid(shopifyByQuery);
        if (queryValid == null) {
            AppInsightsUtils.trackTrace("updateCharsByShopName " + shopName + " 用户  errors queryValid : " + queryValid);
            return false;
        }
        String status = queryValid.getString("status");
        if (!"ACTIVE".equals(status)) {
            return false;
        }

        // 判断是否是订阅计划，是的话，初始化ip，或将ip数清零；不是的话，不管
        if (!gid.contains("AppPurchaseOneTime")){
            iUserIpService.addOrUpdateUserIp(shopName);

            // 将ip额度清零
            iUserIpService.clearIP(shopName);
        }

        return baseMapper.updateCharsByShopName(shopName, chars);
    }

    public boolean updateCharsByShopNameWithoutCheck(String shopName, Integer chars) {
        return baseMapper.updateCharsByShopName(shopName, chars);
    }

    @Override
    public TranslationCounterDO getOneForUpdate(String shopName) {
        return baseMapper.getOneForUpdate(shopName);
    }

    @Override
    public Boolean deleteTrialCounter(String shopName) {
        TranslationCounterDO translationCounterDO = baseMapper.selectOne(new LambdaQueryWrapper<TranslationCounterDO>().eq(TranslationCounterDO::getShopName, shopName));
        if (translationCounterDO != null && translationCounterDO.getOpenAiChars() == 1){
            AppInsightsUtils.trackTrace("deleteTrialCounter " + shopName + " 用户 删除试用额度 " + translationCounterDO.getGoogleChars());
            return baseMapper.update(new LambdaUpdateWrapper<TranslationCounterDO>().eq(TranslationCounterDO::getShopName, shopName).set(TranslationCounterDO::getOpenAiChars, 0).setSql("chars = chars - " + translationCounterDO.getGoogleChars())) > 0;
        }
        return true;
    }

    @Override
    public TranslationCounterDO getTranslationCounterByShopName(String shopName) {
        return baseMapper.selectOne(new LambdaQueryWrapper<TranslationCounterDO>().eq(TranslationCounterDO::getShopName, shopName));
    }


}
