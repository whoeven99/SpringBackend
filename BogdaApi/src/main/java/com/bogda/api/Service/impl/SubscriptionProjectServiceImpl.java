package com.bogda.api.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.api.Service.ISubscriptionProjectService;
import com.bogda.api.entity.DO.SubscriptionProjectDO;
import com.bogda.api.mapper.SubscriptionProjectMapper;
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
