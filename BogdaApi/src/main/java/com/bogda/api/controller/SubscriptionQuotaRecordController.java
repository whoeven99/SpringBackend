package com.bogda.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bogda.api.Service.ISubscriptionQuotaRecordService;
import com.bogda.api.entity.DO.SubscriptionQuotaRecordDO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.bogda.api.utils.CaseSensitiveUtils.appInsights;

@RestController
@RequestMapping("/subscriptionQuotaRecord")
public class SubscriptionQuotaRecordController {
    @Autowired
    private ISubscriptionQuotaRecordService subscriptionQuotaRecordService;

    //在购买订阅之后，给用户添加对应的订阅信息
    @PutMapping("/addSubscriptionQuotaRecord")
    public void addSubscriptionQuotaRecord(@RequestBody SubscriptionQuotaRecordDO subscriptionQuotaRecordDO) {
        Integer i;
        int maxRetries = 3;
        int attempt = 0;
        boolean success = false;

        //如果数据库中含有这条数据，就不插入了
        SubscriptionQuotaRecordDO byId = subscriptionQuotaRecordService.getOne(new QueryWrapper<SubscriptionQuotaRecordDO>().eq("subscription_id", subscriptionQuotaRecordDO.getSubscriptionId()).eq("billing_cycle", 1));
        appInsights.trackTrace("addSubscriptionQuotaRecord " + subscriptionQuotaRecordDO.getSubscriptionId() + " 这条数据只是判断是否要往数据库存值 byId: " + byId);
        if (byId != null){
            appInsights.trackTrace("addSubscriptionQuotaRecord " + subscriptionQuotaRecordDO.getSubscriptionId() + " 数据库中含有这条数据，就不插入了");
            return;
        }
        // 重试逻辑
        while (attempt < maxRetries && !success) {
            try {
                i = subscriptionQuotaRecordService.insertOne(subscriptionQuotaRecordDO.getSubscriptionId(), 1);
                if (i != null && i == 1) {
                    success = true; // 成功条件：i == 1
                } else {
                    attempt++;
                    appInsights.trackTrace("FatalException addSubscriptionQuotaRecord " + subscriptionQuotaRecordDO.getSubscriptionId() + " 插入结果不符合预期 (i=" + i + ")，正在重试第 " + (attempt + 1) + " 次");
                }
            } catch (Exception e) {
                attempt++;
                appInsights.trackTrace("FatalException addSubscriptionQuotaRecord " + subscriptionQuotaRecordDO.getSubscriptionId() + " 插入异常，正在重试第 " + (attempt + 1) + " 次: " + e.getMessage());
            }
        }

    }
}
