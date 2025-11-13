package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.DO.APGUserGeneratedSubtaskDO;

import java.util.Collection;
import java.util.List;

public interface IAPGUserGeneratedSubtaskService extends IService<APGUserGeneratedSubtaskDO> {
    Boolean updateStatusById(String subtaskId, int i);

    Boolean updateAllStatusByUserId(Long id, int i);

    Boolean update34StatusTo9(Long id);

    List<APGUserGeneratedSubtaskDO> getUnfinishedByStatusAndUserId(List<Integer> statusList, Long userId);

    List<APGUserGeneratedSubtaskDO> selectTasksByStatusOrderByCreateTime(int status);

    List<APGUserGeneratedSubtaskDO> selectTasksByUserIdAndStatus(Long userId, int status);
}
