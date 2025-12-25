package com.bogda.common.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogda.common.entity.DO.APGOfficialTemplateDO;

import java.util.List;

public interface IAPGOfficialTemplateService extends IService<APGOfficialTemplateDO> {
    Boolean updateUsedTime(Long templateId);

    List<APGOfficialTemplateDO> selectFirstFiveTemplateId();
}
