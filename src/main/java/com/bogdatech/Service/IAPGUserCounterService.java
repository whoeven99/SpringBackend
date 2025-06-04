package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.DO.APGUserCounterDO;

public interface IAPGUserCounterService extends IService<APGUserCounterDO> {
    Boolean initUserCounter(String shopName);
    APGUserCounterDO getUserCounter(String shopName);
}
