package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.DO.APGUserGeneratedSubtaskDO;

public interface IAPGUserGeneratedSubtaskService extends IService<APGUserGeneratedSubtaskDO> {
    Boolean updateStatusById(String subtaskId, int i);

    Boolean updateAllStatusByUserId(Long id, int i);

    Boolean update34StatusTo9(Long id);
}
