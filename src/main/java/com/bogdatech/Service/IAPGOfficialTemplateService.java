package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.DO.APGOfficialTemplateDO;

import java.util.List;

public interface IAPGOfficialTemplateService extends IService<APGOfficialTemplateDO> {
    Boolean updateUsedTime(Long templateId);

    List<APGOfficialTemplateDO> selectOfficalTemplatesById(List<Long> listOfficeId);

    APGOfficialTemplateDO getOfficialTemplateById(Long templateId);
}
