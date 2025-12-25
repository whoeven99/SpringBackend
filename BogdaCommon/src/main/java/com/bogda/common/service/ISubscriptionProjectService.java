package com.bogda.common.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogda.common.entity.DO.SubscriptionProjectDO;

public interface ISubscriptionProjectService extends IService<SubscriptionProjectDO> {
    Boolean insertSubscriptionProjectDO(SubscriptionProjectDO subscriptionProjectDO);

    SubscriptionProjectDO[] readSubscriptionProject();
}
