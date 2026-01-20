package com.bogda.service.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogda.common.entity.DO.SubscriptionPlansDO;

public interface ISubscriptionPlansService extends IService<SubscriptionPlansDO> {
    Integer getCharsByPlanName(String name);
}
