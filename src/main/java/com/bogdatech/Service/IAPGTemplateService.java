package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.DO.APGTemplateDO;

public interface IAPGTemplateService extends IService<APGTemplateDO> {
    String getTemplateById(Long id);
}
