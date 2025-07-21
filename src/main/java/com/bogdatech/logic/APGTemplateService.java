package com.bogdatech.logic;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bogdatech.Service.IAPGOfficialTemplateService;
import com.bogdatech.Service.IAPGUserTemplateMappingService;
import com.bogdatech.Service.IAPGUserTemplateService;
import com.bogdatech.Service.IAPGUsersService;
import com.bogdatech.entity.DO.APGOfficialTemplateDO;
import com.bogdatech.entity.DO.APGUserTemplateDO;
import com.bogdatech.entity.DO.APGUserTemplateMappingDO;
import com.bogdatech.entity.DO.APGUsersDO;
import com.bogdatech.entity.DTO.TemplateDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

import static com.bogdatech.utils.TypeConversionUtils.officialTemplateToTemplateDTO;
import static com.bogdatech.utils.TypeConversionUtils.userTemplateToTemplateDTO;

@Service
public class APGTemplateService {
    private final IAPGUserTemplateMappingService iapgUserTemplateMappingService;
    private final IAPGUsersService iapgUsersService;
    private final IAPGOfficialTemplateService iapgOfficialTemplateService;
    private final IAPGUserTemplateService iapgUserTemplateService;

    @Autowired
    public APGTemplateService(IAPGUserTemplateMappingService iapgUserTemplateMappingService, IAPGUsersService iapgUsersService, IAPGOfficialTemplateService iapgOfficialTemplateService, IAPGUserTemplateService iapgUserTemplateService) {
        this.iapgUserTemplateMappingService = iapgUserTemplateMappingService;
        this.iapgUsersService = iapgUsersService;
        this.iapgOfficialTemplateService = iapgOfficialTemplateService;
        this.iapgUserTemplateService = iapgUserTemplateService;
    }

    /**
     * 根据用户shopName获取官方模板数据
     * */
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
        templateDTO.setTemplateModel(apgOfficialTemplateDO.getTemplateModel());
        templateDTO.setTemplateSubtype(apgOfficialTemplateDO.getTemplateSubtype());
        return templateDTO;
    }

    /**
     * 初始化默认模板数据，前4条
     * */
    public boolean initializeDefaultTemplate(Long userId) {
        //初始化5条官方模板数据
        boolean save = false;
        for (long i = 2; i <= 5; i++) {
            save = iapgUserTemplateMappingService.save(new APGUserTemplateMappingDO(null, userId, i, false, false));
        }

        return save;
    }

    public Boolean createUserTemplate(String shopName, APGUserTemplateDO apgUserTemplateDO) {
        APGUsersDO userDO = iapgUsersService.getOne(new LambdaQueryWrapper<APGUsersDO>().eq(APGUsersDO::getShopName, shopName));
        if (userDO == null) {
            return false;
        }

        //将用户自定义模板保存到数据库
        apgUserTemplateDO.setUserId(userDO.getId());
        return iapgUserTemplateService.save(apgUserTemplateDO);
    }

    public Boolean deleteUserTemplate(String shopName, TemplateDTO templateDTO) {
        APGUsersDO userDO = iapgUsersService.getOne(new LambdaQueryWrapper<APGUsersDO>().eq(APGUsersDO::getShopName, shopName));
        if (userDO == null) {
            return false;
        }

        //根据传入数据删除用户模板
        APGUserTemplateMappingDO apgUserTemplateMappingDO = new APGUserTemplateMappingDO();
        apgUserTemplateMappingDO.setId(templateDTO.getId());
        apgUserTemplateMappingDO.setUserId(userDO.getId());
        apgUserTemplateMappingDO.setIsDelete(true);
        return iapgUserTemplateMappingService.update(apgUserTemplateMappingDO, new LambdaQueryWrapper<APGUserTemplateMappingDO>().eq(APGUserTemplateMappingDO::getUserId, userDO.getId()).eq(APGUserTemplateMappingDO::getId, templateDTO.getId()));
    }

    /**
     * 获取官方所有模板
     * */
    public List<TemplateDTO> getAllOfficialTemplateData(String templateModel, String templateSubtype) {
        // 对templateModel 和 templateSubtype 做null判断，然后选择对应查询方法
        LambdaQueryWrapper<APGOfficialTemplateDO> queryWrapper = new LambdaQueryWrapper<>();

        if (templateModel != null) {
            queryWrapper.eq(APGOfficialTemplateDO::getTemplateModel, templateModel);
        }
        if (templateSubtype != null) {
            queryWrapper.eq(APGOfficialTemplateDO::getTemplateSubtype, templateSubtype);
        }
        List<APGOfficialTemplateDO> officialTemplates = iapgOfficialTemplateService.list(queryWrapper);
        List<APGOfficialTemplateDO> officialTemplateDOS = new ArrayList<>(officialTemplates);

        if (officialTemplateDOS.isEmpty()){
            return null;
        }
        List<TemplateDTO> templateDTOS = new ArrayList<>();
        //将官方模板转化为 TemplateDTO
        for (APGOfficialTemplateDO apgOfficialTemplateDO: officialTemplateDOS
             ) {
            TemplateDTO templateDTO = officialTemplateToTemplateDTO(apgOfficialTemplateDO);
            templateDTOS.add(templateDTO);
        }
       return templateDTOS;
    }

    public List<TemplateDTO> getAllUserTemplateData(String shopName, String templateModel, String templateSubtype) {
        //获取用户id
        APGUsersDO userDO = iapgUsersService.getOne(new LambdaQueryWrapper<APGUsersDO>().eq(APGUsersDO::getShopName, shopName));
        if (userDO == null) {
            return null;
        }

        //获取用户相关模板
        LambdaQueryWrapper<APGUserTemplateDO> queryWrapper = new LambdaQueryWrapper<>();

        if (templateModel != null) {
            queryWrapper.eq(APGUserTemplateDO::getTemplateModel, templateModel);
        }
        if (templateSubtype != null) {
            queryWrapper.eq(APGUserTemplateDO::getTemplateSubtype, templateSubtype);
        }
        List<APGUserTemplateDO> officialTemplates = iapgUserTemplateService.list(queryWrapper);
        List<APGUserTemplateDO> officialTemplateDOS = new ArrayList<>(officialTemplates);

        if (officialTemplateDOS.isEmpty()){
            return null;
        }
        List<TemplateDTO> templateDTOS = new ArrayList<>();
        //将官方模板转化为 TemplateDTO
        for (APGUserTemplateDO apgUserTemplateDO: officialTemplateDOS
        ) {
            TemplateDTO templateDTO = userTemplateToTemplateDTO(apgUserTemplateDO);
            templateDTOS.add(templateDTO);
        }
        return templateDTOS;
    }
}
