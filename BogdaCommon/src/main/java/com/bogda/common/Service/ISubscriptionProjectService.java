package com.bogda.common.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogda.common.entity.DO.SubscriptionProjectDO;

public interface ISubscriptionProjectService extends IService<SubscriptionProjectDO> {
    Boolean insertSubscriptionProjectDO(SubscriptionProjectDO subscriptionProjectDO);

    SubscriptionProjectDO[] readSubscriptionProject();
}
