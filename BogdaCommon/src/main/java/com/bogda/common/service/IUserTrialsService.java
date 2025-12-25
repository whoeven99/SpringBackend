package com.bogda.common.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogda.common.entity.DO.UserTrialsDO;


public interface IUserTrialsService extends IService<UserTrialsDO> {
    boolean insertUserTrial(String shopName);

    Boolean queryUserTrialByShopName(String shopName);

    boolean updateExpiredByShopName(String shopName);
}
