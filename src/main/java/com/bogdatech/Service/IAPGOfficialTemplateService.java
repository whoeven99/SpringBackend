package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.DO.APGOfficialTemplateDO;

public interface IAPGOfficialTemplateService extends IService<APGOfficialTemplateDO> {
    Boolean updateUsedTime(Long templateId);
}
