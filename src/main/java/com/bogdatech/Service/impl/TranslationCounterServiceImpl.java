package com.bogdatech.Service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.ITranslationCounterService;
import com.bogdatech.entity.DO.TranslationCounterDO;
import com.bogdatech.logic.redis.OrdersRedisService;
import com.bogdatech.mapper.TranslationCounterMapper;
import com.bogdatech.model.controller.request.TranslationCounterRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.bogdatech.requestBody.ShopifyRequestBody.getSingleQuery;
import static com.bogdatech.requestBody.ShopifyRequestBody.getSubscriptionQuery;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.ShopifyUtils.getShopifyByQuery;
import static com.bogdatech.utils.ShopifyUtils.isQueryValid;

@Service
@Transactional
public class TranslationCounterServiceImpl extends ServiceImpl<TranslationCounterMapper, TranslationCounterDO> implements ITranslationCounterService {
    @Autowired
    private OrdersRedisService ordersRedisService;

    @Override
    public TranslationCounterDO readCharsByShopName(String shopName) {
        return baseMapper.readCharsByShopName(shopName);
    }

    @Override
    public int insertCharsByShopName(TranslationCounterRequest translationCounterRequest) {
        return baseMapper.insertCharsByShopName(translationCounterRequest.getShopName(), 0);
    }

    @Override
    public int updateUsedCharsByShopName(TranslationCounterRequest translationCounterRequest) {
        return baseMapper.updateUsedCharsByShopName(translationCounterRequest.getShopName(), translationCounterRequest.getUsedChars());
    }

    @Override
    public Integer getMaxCharsByShopName(String shopName) {
        return baseMapper.getMaxCharsByShopName(shopName);
    }

    @Override
    public Boolean updateCharsByShopName(String shopName, String accessToken, String gid, Integer chars) {
        // 添加订单标识
        ordersRedisService.setOrderId(shopName, gid);

        //根据gid，判断是否符合添加额度的条件
        appInsights.trackTrace("updateCharsByShopName 用户： " + shopName + " gid: " + gid + " chars: " + chars + " accessToken: " + accessToken);
        //根据传来的gid获取， 判断调用那个方法，查询相关订阅信息
        String query;
        if (gid.contains("AppPurchaseOneTime")){
            appInsights.trackTrace("一次性购买 用户： " + shopName);
            query = getSingleQuery(gid);
        }else {
            appInsights.trackTrace("计划购买 用户： " + shopName);
            query = getSubscriptionQuery(gid);
        }
        appInsights.trackTrace("updateCharsByShopName 用户： " + shopName + " query: " + query);
        String shopifyByQuery = getShopifyByQuery(query, shopName, accessToken);
        appInsights.trackTrace("addCharsByShopNameAfterSubscribe " + shopName + " 用户 订阅信息 ：" + shopifyByQuery);
        //判断和解析相关数据
        JSONObject queryValid = isQueryValid(shopifyByQuery);
        if (queryValid == null) {
            appInsights.trackTrace("updateCharsByShopName " + shopName + " 用户  errors queryValid : " + queryValid);
            return false;
        }
        String status = queryValid.getString("status");
        if (!"ACTIVE".equals(status)) {
            return false;
        }

        return baseMapper.updateCharsByShopName(shopName, chars);
    }

    @Override
    public TranslationCounterDO getOneForUpdate(String shopName) {
        return baseMapper.getOneForUpdate(shopName);
    }

    @Override
    public Boolean updateAddUsedCharsByShopName(String shopName, Integer usedChars, Integer maxChars) {
        final int maxRetries = 3;
        final long retryDelayMillis = 1000;
        int retryCount = 0;

        while (retryCount < maxRetries) {
            try {
                Boolean b = baseMapper.updateAddUsedCharsByShopName(shopName, usedChars, maxChars);
                if (Boolean.TRUE.equals(b)) {
                    return true;
                } else {
                    retryCount++;
                    appInsights.trackTrace("updateAddUsedCharsByShopName 更新失败（返回false） errors ，准备第" + retryCount + "次重试，shopName=" + shopName + " usedChars=" + usedChars + ", maxChars=" + maxChars);
                }
            } catch (Exception e) {
                retryCount++;
                appInsights.trackException(e);
                appInsights.trackTrace("updateAddUsedCharsByShopName 更新失败（抛异常） errors ，准备第" + retryCount + "次重试，shopName=" + shopName + " usedChars=" + usedChars + ", maxChars=" + maxChars + ", 错误=" + e);
            }

            try {
                Thread.sleep(retryDelayMillis * maxRetries);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        appInsights.trackTrace("updateAddUsedCharsByShopName 更新失败 errors ，重试" + maxRetries + "次后仍未成功，shopName=" + shopName+ " usedChars=" + usedChars + ", maxChars=" + maxChars);
        return false;
    }

    @Override
    public Boolean deleteTrialCounter(String shopName) {
        TranslationCounterDO translationCounterDO = baseMapper.selectOne(new LambdaQueryWrapper<TranslationCounterDO>().eq(TranslationCounterDO::getShopName, shopName));
        if (translationCounterDO != null && translationCounterDO.getOpenAiChars() == 1){
            appInsights.trackTrace("deleteTrialCounter " + shopName + " 用户 删除试用额度 " + translationCounterDO.getGoogleChars());
            return baseMapper.update(new LambdaUpdateWrapper<TranslationCounterDO>().eq(TranslationCounterDO::getShopName, shopName).set(TranslationCounterDO::getOpenAiChars, 0).setSql("chars = chars - " + translationCounterDO.getGoogleChars())) > 0;
        }
        return true;
    }

    @Override
    public TranslationCounterDO getTranslationCounterByShopName(String shopName) {
        return baseMapper.selectOne(new LambdaQueryWrapper<TranslationCounterDO>().eq(TranslationCounterDO::getShopName, shopName));
    }


}
