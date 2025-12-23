package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.DO.SubscriptionProjectDO;

public interface ISubscriptionProjectService extends IService<SubscriptionProjectDO> {
    Boolean insertSubscriptionProjectDO(SubscriptionProjectDO subscriptionProjectDO);

    SubscriptionProjectDO[] readSubscriptionProject();
}
