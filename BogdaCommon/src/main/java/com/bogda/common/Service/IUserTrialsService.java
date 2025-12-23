package com.bogda.common.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogda.common.entity.DO.UserTrialsDO;

public interface IUserTrialsService extends IService<UserTrialsDO> {
    boolean insertUserTrial(String shopName);

    Boolean queryUserTrialByShopName(String shopName);

    boolean updateExpiredByShopName(String shopName);
}
