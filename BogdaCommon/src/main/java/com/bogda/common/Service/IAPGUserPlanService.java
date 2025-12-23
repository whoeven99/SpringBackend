package com.bogda.common.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogda.common.entity.DO.APGUserPlanDO;

public interface IAPGUserPlanService extends IService<APGUserPlanDO> {
    Boolean initializeFreePlan(Long userId);

    Integer getUserMaxLimit(Long id);
}
