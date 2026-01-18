package com.bogda.service.logic;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bogda.service.Service.IAPGOfficialTemplateService;
import com.bogda.service.Service.IAPGUserTemplateMappingService;
import com.bogda.service.Service.IAPGUserTemplateService;
import com.bogda.service.Service.IAPGUsersService;
import com.bogda.common.entity.DO.APGOfficialTemplateDO;
import com.bogda.common.entity.DO.APGUserTemplateDO;
import com.bogda.common.entity.DO.APGUserTemplateMappingDO;
import com.bogda.common.entity.DO.APGUsersDO;
import com.bogda.common.entity.DTO.TemplateDTO;
import com.bogda.service.utils.TypeConversionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.bogda.service.utils.TypeConversionUtils.officialTemplateToTemplateDTO;
import static com.bogda.service.utils.TypeConversionUtils.userTemplateToTemplateDTO;

@Service
public class APGTemplateService {
    @Autowired
    private IAPGUserTemplateMappingService iapgUserTemplateMappingService;
    @Autowired
    private IAPGUsersService iapgUsersService;
    @Autowired
    private IAPGOfficialTemplateService iapgOfficialTemplateService;
    @Autowired
    private IAPGUserTemplateService iapgUserTemplateService;


    /**
     * 根据用户shopName获取官方模板数据 和 他自己的模板数据
     */
    public List<TemplateDTO> getTemplateByShopName(String shopName) {
        APGUsersDO userDO = iapgUsersService.getOne(new LambdaQueryWrapper<APGUsersDO>().eq(APGUsersDO::getShopName, shopName));

        if (userDO == null) {
            return null;
        }
        List<APGUserTemplateMappingDO> mappingList = iapgUserTemplateMappingService.list(new LambdaQueryWrapper<APGUserTemplateMappingDO>().eq(APGUserTemplateMappingDO::getUserId, userDO.getId()).eq(APGUserTemplateMappingDO::getIsDelete, 0).orderByDesc(APGUserTemplateMappingDO::getUpdateTime));

        // 对List<APGUserTemplateMappingDO>数据进行分析，转化为List<TemplateVO>方法
        return convertToOfficialTemplateVO(mappingList);
    }

    /**
     * 对List<APGUserTemplateMappingDO>数据进行分析，转化为List<TemplateVO>方法
     */
    public List<TemplateDTO> convertToOfficialTemplateVO(List<APGUserTemplateMappingDO> mappingList) {
        // 空检查：直接返回空集合
        if (mappingList == null || mappingList.isEmpty()) {
            return Collections.emptyList();
        }

        // 分离官方模板与用户模板ID
        List<Long> officialIds = new ArrayList<>();
        List<Long> userIds = new ArrayList<>();

        for (APGUserTemplateMappingDO mapping : mappingList) {
            if (Boolean.FALSE.equals(mapping.getTemplateType())) {
                officialIds.add(mapping.getTemplateId());
            } else {
                userIds.add(mapping.getTemplateId());
            }
        }

        List<TemplateDTO> templates = new ArrayList<>();

        // 查询并转换用户模板
        if (!userIds.isEmpty()) {
            List<APGUserTemplateDO> userTemplates = iapgUserTemplateService.list(
                    new LambdaQueryWrapper<APGUserTemplateDO>()
                            .in(APGUserTemplateDO::getId, userIds)
                            .orderByDesc(APGUserTemplateDO::getUpdateTime));

            if (userTemplates != null) {
                templates.addAll(
                        userTemplates.stream()
                                .map(TypeConversionUtils::userTemplateToTemplateDTO).toList());
            }
        }

        // 查询并转换官方模板
        if (!officialIds.isEmpty()) {
            List<APGOfficialTemplateDO> officialTemplates = iapgOfficialTemplateService.list(
                    new LambdaQueryWrapper<APGOfficialTemplateDO>()
                            .in(APGOfficialTemplateDO::getId, officialIds)
                            .orderByDesc(APGOfficialTemplateDO::getUpdateTime));

            if (officialTemplates != null) {
                templates.addAll(
                        officialTemplates.stream()
                                .map(TypeConversionUtils::officialTemplateToTemplateDTO)
                                .toList());
            }
        }

        return templates;
    }


    /**
     * 初始化默认模板数据，前4条
     */
    public void initializeDefaultTemplate(Long userId) {
        // 获取前5条模板id
        List<APGOfficialTemplateDO> apgOfficialTemplateDOS = iapgOfficialTemplateService.selectFirstFiveTemplateId();

        List<Long> templateIds = apgOfficialTemplateDOS.stream().map(APGOfficialTemplateDO::getId).toList();

        // 构建批量插入列表
        for (Long templateId : templateIds
             ) {
            APGUserTemplateMappingDO apgUserTemplateMappingDO = new APGUserTemplateMappingDO(
                    null,        // id 自增
                    userId,      // 用户ID
                    templateId,   // 模板ID
                    false,       // templateType: false 表示官方模板
                    false,       // 其他标志位
                    LocalDateTime.now()         // 其他字段
            );
            iapgUserTemplateMappingService.save(apgUserTemplateMappingDO);

        }

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
     */
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
        if (officialTemplateDOS.isEmpty()) {
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

        if (officialTemplateDOS.isEmpty()) {
            return null;
        }
        List<TemplateDTO> templateDTOS = new ArrayList<>();
        //将官方模板转化为 TemplateDTO
        for (APGUserTemplateDO apgUserTemplateDO : officialTemplateDOS
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
