package com.bogda.common.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogda.common.entity.DO.UserIPSwitchDO;

public interface IUserIPSwitchService extends IService<UserIPSwitchDO> {
    int insertSwitch(UserIPSwitchDO userIPSwitchDO);

    Integer getSwitchId(String shopName);
}
