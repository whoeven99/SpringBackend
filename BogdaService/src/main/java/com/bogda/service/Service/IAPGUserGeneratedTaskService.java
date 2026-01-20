package com.bogda.service.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogda.common.entity.DO.APGUserGeneratedTaskDO;

public interface IAPGUserGeneratedTaskService extends IService<APGUserGeneratedTaskDO> {
    Boolean updateStatusByUserId(Long userId, int i);

    Boolean updateStatusTo2(Long id);
}
