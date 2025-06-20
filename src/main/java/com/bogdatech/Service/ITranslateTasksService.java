package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.DO.TranslateTasksDO;
import com.bogdatech.entity.VO.RabbitMqTranslateVO;

public interface ITranslateTasksService extends IService<TranslateTasksDO> {

    RabbitMqTranslateVO getDataToProcess(String taskId);
}
