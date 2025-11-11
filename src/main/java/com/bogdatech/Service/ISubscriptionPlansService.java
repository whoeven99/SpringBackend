package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.DO.SubscriptionPlansDO;
import com.volcengine.model.request.GetDailyMarketingPackageRequest;

public interface ISubscriptionPlansService extends IService<SubscriptionPlansDO> {
    Integer getCharsByPlanName(String name);

    SubscriptionPlansDO getSubscriptionPlanByPlanId(Integer planId);
}
