package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.DO.APGTemplateDO;

import java.util.List;

public interface IAPGTemplateService extends IService<APGTemplateDO> {
    String getTemplateById(Long id);

    List<APGTemplateDO> getAllTemplateData(String shopName);
}
