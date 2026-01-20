package com.bogda.repository.sql;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogda.common.entity.DO.SubscriptionPlansDO;

public interface ISubscriptionPlansService extends IService<SubscriptionPlansDO> {
    Integer getCharsByPlanName(String name);
}
