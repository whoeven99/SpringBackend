package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.DO.UserTrialsDO;

public interface IUserTrialsService extends IService<UserTrialsDO> {
    boolean insertUserTrial(String shopName);
}
