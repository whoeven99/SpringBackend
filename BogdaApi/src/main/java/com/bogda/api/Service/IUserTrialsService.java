package com.bogda.api.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogda.api.entity.DO.UserTrialsDO;

public interface IUserTrialsService extends IService<UserTrialsDO> {
    boolean insertUserTrial(String shopName);

    Boolean queryUserTrialByShopName(String shopName);

    boolean updateExpiredByShopName(String shopName);
}
