package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.DO.TranslateTasksDO;
import com.bogdatech.entity.VO.RabbitMqTranslateVO;

import java.util.List;

public interface ITranslateTasksService extends IService<TranslateTasksDO> {

    RabbitMqTranslateVO getDataToProcess(String taskId);

    List<TranslateTasksDO> find0StatusTasks();

    boolean updateByTaskId(String taskId, Integer status);

    int updateStatus2To3ByShopName(String shopName);

    int deleteStatus1Data();

    int updateByShopName(String shopName, int i);
}
