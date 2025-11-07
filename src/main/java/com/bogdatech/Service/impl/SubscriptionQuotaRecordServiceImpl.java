package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.ISubscriptionQuotaRecordService;
import com.bogdatech.entity.DO.SubscriptionQuotaRecordDO;
import com.bogdatech.mapper.SubscriptionQuotaRecordMapper;
import org.springframework.stereotype.Service;

@Service
public class SubscriptionQuotaRecordServiceImpl extends ServiceImpl<SubscriptionQuotaRecordMapper, SubscriptionQuotaRecordDO> implements ISubscriptionQuotaRecordService {
    @Override
    public Integer insertOne(String subscriptionId, int billingCycle) {
        return baseMapper.insert(new SubscriptionQuotaRecordDO(null, subscriptionId, billingCycle));
    }

    @Override
    public SubscriptionQuotaRecordDO getSubscriptionQuotaRecordDataByIdAndBillingCycle(String subscriptionId, int billingCycle) {
        return baseMapper.selectOne(new LambdaQueryWrapper<SubscriptionQuotaRecordDO>().eq(SubscriptionQuotaRecordDO::getSubscriptionId
                        , subscriptionId).eq(SubscriptionQuotaRecordDO::getBillingCycle, billingCycle));
    }
}
