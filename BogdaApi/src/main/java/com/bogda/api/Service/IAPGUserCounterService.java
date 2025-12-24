package com.bogda.api.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogda.api.entity.DO.APGUserCounterDO;

public interface IAPGUserCounterService extends IService<APGUserCounterDO> {
    Boolean initUserCounter(String shopName);
    APGUserCounterDO getUserCounter(String shopName);

    Boolean updateUserUsedCount(Long userId, Integer counter, Integer maxLimit);

    Boolean updateCharsByUserId(Long id);

    Boolean updateUserToken(Long id, Integer token);
}
