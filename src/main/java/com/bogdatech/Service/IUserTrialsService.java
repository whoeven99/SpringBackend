package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.DO.UserTrialsDO;

import java.util.List;

public interface IUserTrialsService extends IService<UserTrialsDO> {
    boolean insertUserTrial(String shopName);

    Boolean queryUserTrialByShopName(String shopName);

    UserTrialsDO getUserTrialByShopName(String shopName);

    boolean updateTrialsExpiredByShopName(String shopName, boolean trialsExpired);

    boolean updateTrialShowByShopName(String shopName, boolean b);

    List<UserTrialsDO> selectTrialsByIsTrialExpired(boolean trialExpired);
}
