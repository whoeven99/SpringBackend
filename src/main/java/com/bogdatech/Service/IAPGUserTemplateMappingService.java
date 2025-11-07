package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.DO.APGUserTemplateMappingDO;

import java.util.List;

public interface IAPGUserTemplateMappingService extends IService<APGUserTemplateMappingDO> {
    List<APGUserTemplateMappingDO> selectMappingByIdNotDeleted(Long id, int isDeleted);

    Boolean updateUserTemplateByUserIdAndTemplateId(APGUserTemplateMappingDO apgUserTemplateMappingDO, Long userId, Long templateId);
}
