package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.UserIPSwitchDO;

public interface IUserIPSwitchService extends IService<UserIPSwitchDO> {
    int insertSwitch(UserIPSwitchDO userIPSwitchDO);

    Integer getSwitchId(String shopName);
}
