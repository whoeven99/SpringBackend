package com.bogda.api.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogda.api.entity.DO.APGUserGeneratedTaskDO;

public interface IAPGUserGeneratedTaskService extends IService<APGUserGeneratedTaskDO> {
    Boolean updateStatusByUserId(Long userId, int i);

    Boolean updateStatusTo2(Long id);
}
