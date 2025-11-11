package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.DO.APGUserTemplateDO;

import java.util.List;

public interface IAPGUserTemplateService extends IService<APGUserTemplateDO> {
    List<APGUserTemplateDO> selectUserTemplatesById(List<Long> listUserId);

    APGUserTemplateDO getUserTemplateByUserIdAndTemplateId(Long userId, Long templateId);
}
