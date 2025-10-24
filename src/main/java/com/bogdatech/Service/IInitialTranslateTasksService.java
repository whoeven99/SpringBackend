package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.DO.InitialTranslateTasksDO;

public interface IInitialTranslateTasksService extends IService<InitialTranslateTasksDO> {
    boolean updateStatusByTaskId(String taskId, int i);
}
