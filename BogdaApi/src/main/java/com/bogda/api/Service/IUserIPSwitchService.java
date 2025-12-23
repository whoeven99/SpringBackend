package com.bogda.api.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogda.api.entity.DO.UserIPSwitchDO;

public interface IUserIPSwitchService extends IService<UserIPSwitchDO> {
    int insertSwitch(UserIPSwitchDO userIPSwitchDO);

    Integer getSwitchId(String shopName);
}
