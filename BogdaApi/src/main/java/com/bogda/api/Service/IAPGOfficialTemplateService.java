package com.bogda.api.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogda.api.entity.DO.APGOfficialTemplateDO;

import java.util.List;

public interface IAPGOfficialTemplateService extends IService<APGOfficialTemplateDO> {
    Boolean updateUsedTime(Long templateId);

    List<APGOfficialTemplateDO> selectFirstFiveTemplateId();
}
