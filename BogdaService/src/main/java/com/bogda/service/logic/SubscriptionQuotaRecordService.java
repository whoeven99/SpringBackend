package com.bogda.service.logic;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bogda.common.controller.response.BaseResponse;
import com.bogda.common.utils.AppInsightsUtils;
import com.bogda.repository.entity.SubscriptionQuotaRecordDO;
import com.bogda.repository.repo.SubscriptionQuotaRecordRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionQuotaRecordService {
    @Autowired
    private SubscriptionQuotaRecordRepo subscriptionQuotaRecordRepo;

    public BaseResponse<Object> addSubscriptionQuotaRecord(SubscriptionQuotaRecordDO subscriptionQuotaRecordDO) {

        Integer i;
        int maxRetries = 3;
        int attempt = 0;
        boolean success = false;

        //如果数据库中含有这条数据，就不插入了
        SubscriptionQuotaRecordDO byId = subscriptionQuotaRecordRepo.getOne(new QueryWrapper<SubscriptionQuotaRecordDO>().eq("subscription_id", subscriptionQuotaRecordDO.getSubscriptionId()).eq("billing_cycle", 1));
        AppInsightsUtils.trackTrace("addSubscriptionQuotaRecord " + subscriptionQuotaRecordDO.getSubscriptionId() + " 这条数据只是判断是否要往数据库存值 byId: " + byId);
        if (byId != null){
            AppInsightsUtils.trackTrace("addSubscriptionQuotaRecord " + subscriptionQuotaRecordDO.getSubscriptionId() + " 数据库中含有这条数据，就不插入了");
            return new BaseResponse<>().CreateSuccessResponse("");
        }
        // 重试逻辑
        while (attempt < maxRetries && !success) {
            try {
                i = subscriptionQuotaRecordRepo.saveNewRecord(subscriptionQuotaRecordDO.getSubscriptionId(), 1);

                if (i != null && i == 1) {
                    success = true; // 成功条件：i == 1
                    return new BaseResponse<>().CreateSuccessResponse(i);
                } else {
                    attempt++;
                    AppInsightsUtils.trackTrace("FatalException addSubscriptionQuotaRecord " + subscriptionQuotaRecordDO.getSubscriptionId() + " 插入结果不符合预期 (i=" + i + ")，正在重试第 " + (attempt + 1) + " 次");
                }
            } catch (Exception e) {
                attempt++;
                AppInsightsUtils.trackTrace("FatalException addSubscriptionQuotaRecord " + subscriptionQuotaRecordDO.getSubscriptionId() + " 插入异常，正在重试第 " + (attempt + 1) + " 次: " + e.getMessage());
            }
        }
        return new BaseResponse<>().CreateErrorResponse(false);
    }
}
