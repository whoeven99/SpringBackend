package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.DO.InitialTranslateTasksDO;

import java.util.List;

public interface IInitialTranslateTasksService extends IService<InitialTranslateTasksDO> {
    boolean updateStatusByTaskId(String taskId, int i);

    List<InitialTranslateTasksDO> selectTop10Tasks(int status, String auto);
}
