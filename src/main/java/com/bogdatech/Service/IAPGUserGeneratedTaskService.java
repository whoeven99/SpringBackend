package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.DO.APGUserGeneratedTaskDO;

public interface IAPGUserGeneratedTaskService extends IService<APGUserGeneratedTaskDO> {
    Boolean updateStatusByUserId(Long userId, int i);

    Boolean updateStatusTo2(Long id);

    APGUserGeneratedTaskDO getTaskByUserId(Long id);

    Boolean updateTaskByUserId(APGUserGeneratedTaskDO apgUserGeneratedTaskDO, Long id);
}
