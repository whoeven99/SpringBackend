package com.bogdatech.logic;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bogdatech.Service.IAPGOfficialTemplateService;
import com.bogdatech.Service.IAPGUserTemplateMappingService;
import com.bogdatech.Service.IAPGUsersService;
import com.bogdatech.entity.DO.APGOfficialTemplateDO;
import com.bogdatech.entity.DO.APGUserTemplateMappingDO;
import com.bogdatech.entity.DO.APGUsersDO;
import com.bogdatech.entity.DTO.TemplateDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class APGTemplateService {
    private final IAPGUserTemplateMappingService iapgUserTemplateMappingService;
    private final IAPGUsersService iapgUsersService;
    private final IAPGOfficialTemplateService iapgOfficialTemplateService;

    @Autowired
    public APGTemplateService(IAPGUserTemplateMappingService iapgUserTemplateMappingService, IAPGUsersService iapgUsersService, IAPGOfficialTemplateService iapgOfficialTemplateService) {
        this.iapgUserTemplateMappingService = iapgUserTemplateMappingService;
        this.iapgUsersService = iapgUsersService;
        this.iapgOfficialTemplateService = iapgOfficialTemplateService;
    }

    public List<TemplateDTO> getTemplateByShopName(String shopName) {
        APGUsersDO userDO = iapgUsersService.getOne(new LambdaQueryWrapper<APGUsersDO>().eq(APGUsersDO::getShopName, shopName));
        if (userDO == null) {
            return null;
        }
        List<APGUserTemplateMappingDO> mappingList = iapgUserTemplateMappingService.list(new LambdaQueryWrapper<APGUserTemplateMappingDO>().eq(APGUserTemplateMappingDO::getUserId, userDO.getId()).eq(APGUserTemplateMappingDO::getIsDelete, 0));
        //对List<APGUserTemplateMappingDO>数据进行分析，转化为List<TemplateVO>方法
        return convertToOfficialTemplateVO(mappingList);
    }

    /**
     * 对List<APGUserTemplateMappingDO>数据进行分析，转化为List<TemplateVO>方法
     */
    public List<TemplateDTO> convertToOfficialTemplateVO(List<APGUserTemplateMappingDO> mappingList) {
        //获取官方模板的数据并返回
        List<Long> listId = new ArrayList<>();
        if (mappingList == null || mappingList.isEmpty()) {
            return null;
        }
        for (APGUserTemplateMappingDO apgUserTemplateMappingDO : mappingList
        ) {
            //templateType为false时，为官方模板
            if (!apgUserTemplateMappingDO.getTemplateType()) {
                listId.add(apgUserTemplateMappingDO.getTemplateId());
            }
        }
        //通过listId获取官方模板
        List<APGOfficialTemplateDO> apgOfficialTemplates = iapgOfficialTemplateService.listByIds(listId);
        List<TemplateDTO> templates = new ArrayList<>();
        //将官方模板转化为 TemplateDTO
        for (APGOfficialTemplateDO apgOfficialTemplateDO : apgOfficialTemplates
        ) {
            TemplateDTO templateDTO = convertOfficialToTemplateDTO(apgOfficialTemplateDO);
            templates.add(templateDTO);
        }

        return templates;
    }

    /**
     * 将APGOfficialTemplateDO转化为TemplateDTO
     */
    public TemplateDTO convertOfficialToTemplateDTO(APGOfficialTemplateDO apgOfficialTemplateDO) {
        TemplateDTO templateDTO = new TemplateDTO();
        templateDTO.setId(apgOfficialTemplateDO.getId());
        templateDTO.setTemplateData(apgOfficialTemplateDO.getTemplateData());
        templateDTO.setTemplateDescription(apgOfficialTemplateDO.getTemplateDescription());
        templateDTO.setTemplateType(apgOfficialTemplateDO.getTemplateType());
        templateDTO.setTemplateTitle(apgOfficialTemplateDO.getTemplateTitle());
        templateDTO.setTemplateClass(false);
        return templateDTO;
    }

    /**
     * 初始化默认模板数据，前4条
     * */
    public boolean InitializeDefaultTemplate(String shopName) {
        APGUsersDO userDO = iapgUsersService.getOne(new LambdaQueryWrapper<APGUsersDO>().eq(APGUsersDO::getShopName, shopName));
        if (userDO == null) {
            return false;
        }

        //初始化5条官方模板数据
        boolean save = false;
        for (long i = 2; i <= 5; i++) {
            save = iapgUserTemplateMappingService.save(new APGUserTemplateMappingDO(null, userDO.getId(), i, false, false));
        }

        return save;
    }
}
