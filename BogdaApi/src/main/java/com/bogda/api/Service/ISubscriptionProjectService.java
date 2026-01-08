package com.bogda.api.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogda.api.entity.DO.SubscriptionProjectDO;

public interface ISubscriptionProjectService extends IService<SubscriptionProjectDO> {
    Boolean insertSubscriptionProjectDO(SubscriptionProjectDO subscriptionProjectDO);

    SubscriptionProjectDO[] readSubscriptionProject();
}
