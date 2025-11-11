package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.DO.InitialTranslateTasksDO;

import java.util.List;

public interface IInitialTranslateTasksService extends IService<InitialTranslateTasksDO> {
    boolean updateStatusByTaskId(String taskId, int status);

    List<InitialTranslateTasksDO> selectTop10Tasks(int status, String manual);

    boolean updateAutoInitialDataByShopNameAndStatus(String shopName, int sourceStatus, List<String> targetList, int targetStatus, boolean isSendEmail, boolean isDeleted);

    boolean deleteInitialTasksByShopNameAndSourceAndTargetAndTaskType(String shopName, String source, String manual);

    List<InitialTranslateTasksDO> selectTasksByStatusAndNotSendEmail(List<Integer> statusList, boolean isSendEmail);

    boolean updateStatusAndSendEmailByTaskId(String taskId, int status, boolean isSendEmail);

    List<InitialTranslateTasksDO> selectTasksByShopNameAndIsDeleted(String shopName);

    List<InitialTranslateTasksDO> selectTasksByShopNameAndIsDeletedOrderByCreatedAt(String shopName);

    List<InitialTranslateTasksDO> selectTaskByStatusOrderByCreatedAt(int status);
}
