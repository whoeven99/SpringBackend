package com.bogda.service.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogda.service.entity.DO.UserIPSwitchDO;

public interface IUserIPSwitchService extends IService<UserIPSwitchDO> {
    int insertSwitch(UserIPSwitchDO userIPSwitchDO);

    Integer getSwitchId(String shopName);
}
