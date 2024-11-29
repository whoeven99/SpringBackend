package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.ISubscriptionProjectService;
import com.bogdatech.entity.SubscriptionProjectDO;
import com.bogdatech.mapper.SubscriptionProjectMapper;
import org.springframework.stereotype.Service;

@Service
public class SubscriptionProjectServiceImpl extends ServiceImpl<SubscriptionProjectMapper,SubscriptionProjectDO> implements ISubscriptionProjectService {

    @Override
    public Boolean insertSubscriptionProjectDO(SubscriptionProjectDO subscriptionProjectDO) {
        return baseMapper.insert(subscriptionProjectDO) > 0;
    }

    @Override
    public SubscriptionProjectDO[] readSubscriptionProject() {
        return baseMapper.readSubscriptionProject();
    }
}
