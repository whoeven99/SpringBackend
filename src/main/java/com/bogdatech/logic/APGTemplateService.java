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
import java.util.Set;
import java.util.stream.Collectors;

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
     * 根据用户shopName获取官方模板数据 和 他自己的模板数据
     * */
    public List<TemplateDTO> getTemplateByShopName(String shopName) {
        APGUsersDO userDO = iapgUsersService.getOne(new LambdaQueryWrapper<APGUsersDO>().eq(APGUsersDO::getShopName, shopName));
        if (userDO == null) {
            return null;
        }
        List<APGUserTemplateMappingDO> mappingList = iapgUserTemplateMappingService.list(new LambdaQueryWrapper<APGUserTemplateMappingDO>().eq(APGUserTemplateMappingDO::getUserId, userDO.getId()).eq(APGUserTemplateMappingDO::getIsDelete, 0).orderByDesc(APGUserTemplateMappingDO::getUpdateTime));
        //对List<APGUserTemplateMappingDO>数据进行分析，转化为List<TemplateVO>方法
        return convertToOfficialTemplateVO(mappingList);
    }

    /**
     * 对List<APGUserTemplateMappingDO>数据进行分析，转化为List<TemplateVO>方法
     */
    public List<TemplateDTO> convertToOfficialTemplateVO(List<APGUserTemplateMappingDO> mappingList) {
        //获取官方模板的数据并返回
        List<Long> listOfficeId = new ArrayList<>();
        List<Long> listUserId = new ArrayList<>();
        if (mappingList == null || mappingList.isEmpty()) {
            return null;
        }

        for (APGUserTemplateMappingDO apgUserTemplateMappingDO : mappingList
        ) {
            //templateType为false时，为官方模板
            if (!apgUserTemplateMappingDO.getTemplateType()) {
                listOfficeId.add(apgUserTemplateMappingDO.getTemplateId());
            }else {
                listUserId.add(apgUserTemplateMappingDO.getTemplateId());
            }
        }

        //通过listId获取官方模板
        List<APGOfficialTemplateDO> apgOfficialTemplates = iapgOfficialTemplateService.list(
                new LambdaQueryWrapper<APGOfficialTemplateDO>()
                .in(APGOfficialTemplateDO::getId, listOfficeId)
                .orderByDesc(APGOfficialTemplateDO::getUpdateTime));

        if (apgOfficialTemplates == null || apgOfficialTemplates.isEmpty()){
            return null;
        }

        List<TemplateDTO> templates = new ArrayList<>();

        //获取用户模板
        if (listUserId.isEmpty()){
            return templates;
        }
        List<APGUserTemplateDO> userTemplates = iapgUserTemplateService.list(
                new LambdaQueryWrapper<APGUserTemplateDO>()
                        .in(APGUserTemplateDO::getId, listUserId)
                        .orderByDesc(APGUserTemplateDO::getUpdateTime));
        if (userTemplates == null || userTemplates.isEmpty()){
            return templates;
        }

        for (APGUserTemplateDO userTemplateDO : userTemplates){
            TemplateDTO templateDTO = userTemplateToTemplateDTO(userTemplateDO);
            templates.add(templateDTO);
        }

        //将官方模板转化为 TemplateDTO
        for (APGOfficialTemplateDO apgOfficialTemplateDO : apgOfficialTemplates
        ) {
            TemplateDTO templateDTO = officialTemplateToTemplateDTO(apgOfficialTemplateDO);
            templates.add(templateDTO);
        }

        return templates;
    }

    /**
     * 初始化默认模板数据，前4条
     * */
    public boolean initializeDefaultTemplate(Long userId) {
        //初始化5条官方模板数据
        boolean save = false;
        for (long i = 2; i <= 5; i++) {
            save = iapgUserTemplateMappingService.save(new APGUserTemplateMappingDO(null, userId, i, false, false, null));
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
        boolean save = iapgUserTemplateService.save(apgUserTemplateDO);

        //将模板和用户绑定
        APGUserTemplateMappingDO apgUserTemplateMappingDO = new APGUserTemplateMappingDO(null, userDO.getId(), apgUserTemplateDO.getId(), true, false, null);
        boolean binding = iapgUserTemplateMappingService.save(apgUserTemplateMappingDO);
        return binding && save;
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
        apgUserTemplateMappingDO.setTemplateType(templateDTO.getTemplateClass());
        apgUserTemplateMappingDO.setIsDelete(true);
        return iapgUserTemplateMappingService.update(apgUserTemplateMappingDO, new LambdaQueryWrapper<APGUserTemplateMappingDO>().eq(APGUserTemplateMappingDO::getUserId, userDO.getId()).eq(APGUserTemplateMappingDO::getTemplateId, templateDTO.getId()));
    }

    /**
     * 获取官方所有模板
     * */
    public List<TemplateDTO> getAllOfficialTemplateData(Long userId, String templateModel, String templateSubtype, String templateType) {
        // 对templateModel 和 templateSubtype 做null判断，然后选择对应查询方法
        LambdaQueryWrapper<APGOfficialTemplateDO> queryWrapper = new LambdaQueryWrapper<>();

        if (templateModel != null) {
            queryWrapper.eq(APGOfficialTemplateDO::getTemplateModel, templateModel);
        }
        if (templateSubtype != null) {
            queryWrapper.eq(APGOfficialTemplateDO::getTemplateSubtype, templateSubtype);
        }
        if (templateType != null) {
            queryWrapper.eq(APGOfficialTemplateDO::getTemplateType, templateType);
        }

        List<APGOfficialTemplateDO> officialTemplateDOS = new ArrayList<>(iapgOfficialTemplateService.list(queryWrapper));
        // 获取用户映射关系，用于判断官方模板数据是否被添加过
        List<APGUserTemplateMappingDO> mappingList = iapgUserTemplateMappingService.list(new LambdaQueryWrapper<APGUserTemplateMappingDO>().eq(APGUserTemplateMappingDO::getUserId, userId).eq(APGUserTemplateMappingDO::getIsDelete, false));
        if (officialTemplateDOS.isEmpty()){
            return null;
        }
        // 将用户已使用的模板ID提取出来（官方模板对应的 mapping 是 templateType == false）
        Set<Long> userUsedTemplateIds = mappingList.stream()
                .filter(mapping -> Boolean.FALSE.equals(mapping.getTemplateType()))
                .map(APGUserTemplateMappingDO::getTemplateId)
                .collect(Collectors.toSet());


        //将官方模板转化为 TemplateDTO
        return officialTemplateDOS.stream().map(templateDO -> {
            TemplateDTO dto = officialTemplateToTemplateDTO(templateDO);
            dto.setIsUserUsed(userUsedTemplateIds.contains(dto.getId()));
            return dto;
        }).collect(Collectors.toList());
    }

    public List<TemplateDTO> getAllUserTemplateData(Long userId, String templateModel, String templateSubtype, String templateType) {

        //获取用户相关模板
        LambdaQueryWrapper<APGUserTemplateDO> queryWrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            queryWrapper.eq(APGUserTemplateDO::getUserId, userId);
        }
        if (templateModel != null) {
            queryWrapper.eq(APGUserTemplateDO::getTemplateModel, templateModel);
        }
        if (templateSubtype != null) {
            queryWrapper.eq(APGUserTemplateDO::getTemplateSubtype, templateSubtype);
        }
        if (templateType != null) {
            queryWrapper.eq(APGUserTemplateDO::getTemplateType, templateType);
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

    public Boolean addOfficialOrUserTemplate(String shopName, Long templateId, Boolean templateType) {
        APGUsersDO userDO = iapgUsersService.getOne(new LambdaQueryWrapper<APGUsersDO>().eq(APGUsersDO::getShopName, shopName));
        if (userDO == null) {
            return false;
        }

        //修改templateId对应的使用次数
        iapgOfficialTemplateService.updateUsedTime(templateId);
        APGUserTemplateMappingDO apgUserTemplateMappingDO = new APGUserTemplateMappingDO();
        apgUserTemplateMappingDO.setTemplateId(templateId);
        apgUserTemplateMappingDO.setUserId(userDO.getId());
        apgUserTemplateMappingDO.setTemplateType(templateType);
        return iapgUserTemplateMappingService.save(apgUserTemplateMappingDO);
    }

    public String previewExampleDataByTemplateId(String shopName, Long templateId) {
        return iapgOfficialTemplateService.getOne(new LambdaQueryWrapper<APGOfficialTemplateDO>().eq(APGOfficialTemplateDO::getId, templateId)).getExampleDate();
    }
}
