package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.DO.SubscriptionPlansDO;

public interface ISubscriptionPlansService extends IService<SubscriptionPlansDO> {
    Integer getCharsByPlanName(String name);
}
