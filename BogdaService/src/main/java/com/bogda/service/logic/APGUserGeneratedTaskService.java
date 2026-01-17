package com.bogda.service.logic;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bogda.service.Service.IAPGUserGeneratedSubtaskService;
import com.bogda.service.Service.IAPGUserGeneratedTaskService;
import com.bogda.service.Service.IAPGUsersService;
import com.bogda.service.entity.DO.APGUserGeneratedSubtaskDO;
import com.bogda.service.entity.DO.APGUserGeneratedTaskDO;
import com.bogda.service.entity.DO.APGUsersDO;
import com.bogda.service.entity.VO.GenerateDescriptionVO;
import com.bogda.service.entity.VO.GenerateDescriptionsVO;
import com.bogda.service.entity.VO.GenerateEmailVO;
import com.bogda.service.entity.VO.GenerateProgressBarVO;
import com.bogda.common.contants.TranslateConstants;
import com.bogda.common.utils.AppInsightsUtils;
import com.bogda.common.utils.JsonUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

import static com.bogda.service.task.GenerateDbTask.GENERATE_SHOP_BAR;

@Service
public class APGUserGeneratedTaskService {
    @Autowired
    private IAPGUsersService iapgUsersService;
    @Autowired
    private IAPGUserGeneratedTaskService iapgUserGeneratedTaskService;
    @Autowired
    private IAPGUserGeneratedSubtaskService iapgUserGeneratedSubtaskService;
    public static final ConcurrentHashMap<Long, Integer> GENERATE_STATE_BAR = new ConcurrentHashMap<>();
    public static final Integer INITIALIZATION = 1;
    public static final Integer GENERATING = 2;
    public static final Integer FINISHED = 3;

    /**
     * 初始化或更新相关数据
     */
    public Boolean initOrUpdateData(String shopName, Integer status, String taskModel, String taskData) {
        APGUsersDO userDO = iapgUsersService.getOne(new LambdaQueryWrapper<APGUsersDO>().eq(APGUsersDO::getShopName, shopName));
        if (userDO == null) {
            return false;
        }

        //获取用户任务状态
        APGUserGeneratedTaskDO taskDO = iapgUserGeneratedTaskService.getOne(new LambdaQueryWrapper<APGUserGeneratedTaskDO>().eq(APGUserGeneratedTaskDO::getUserId, userDO.getId()));
        APGUserGeneratedTaskDO apgUserGeneratedTaskDO = new APGUserGeneratedTaskDO(null, userDO.getId(), null, taskModel, taskData, null);
        if (taskDO == null) {
            //插入对应数据
            return iapgUserGeneratedTaskService.save(apgUserGeneratedTaskDO);
        }

        //更新对应数据
        apgUserGeneratedTaskDO.setTaskStatus(status);
        apgUserGeneratedTaskDO.setTaskData(taskData);
        return iapgUserGeneratedTaskService.update(apgUserGeneratedTaskDO, new LambdaUpdateWrapper<APGUserGeneratedTaskDO>().eq(APGUserGeneratedTaskDO::getUserId, userDO.getId()));
    }

    public GenerateProgressBarVO getUserData(String shopName) {
        APGUsersDO userDO = iapgUsersService.getOne(new LambdaQueryWrapper<APGUsersDO>().eq(APGUsersDO::getShopName, shopName));
        if (userDO == null) {
            return null;
        }

        GenerateProgressBarVO generateProgressBarVO = new GenerateProgressBarVO();
        APGUserGeneratedTaskDO taskDO = iapgUserGeneratedTaskService.getOne(new LambdaQueryWrapper<APGUserGeneratedTaskDO>().eq(APGUserGeneratedTaskDO::getUserId, userDO.getId()));

        if (taskDO == null) {
            return generateProgressBarVO;
        }

        try {
            GenerateDescriptionsVO generateDescriptionsVO = JsonUtils.OBJECT_MAPPER.readValue(taskDO.getTaskData(), GenerateDescriptionsVO.class);
            Integer totalCount = generateDescriptionsVO.getProductIds().length;
            Integer unfinishedCount = iapgUserGeneratedSubtaskService.list(new LambdaQueryWrapper<APGUserGeneratedSubtaskDO>()
                    .in(APGUserGeneratedSubtaskDO::getStatus, Arrays.asList(0, 3, 4))
                    .eq(APGUserGeneratedSubtaskDO::getUserId, userDO.getId())).size();
            generateProgressBarVO = apgUserGeneratedTaskDOToGenerateProgressBarVO(taskDO, totalCount, unfinishedCount);
            generateProgressBarVO.setProductTitle(GENERATE_SHOP_BAR.get(userDO.getId()));
            generateProgressBarVO.setStatus(GENERATE_STATE_BAR.get(userDO.getId()));
            generateProgressBarVO.setTaskTime(taskDO.getUpdateTime());//获取对应的时间
            //获取产品标题
            return generateProgressBarVO;
        } catch (Exception e) {
            AppInsightsUtils.trackTrace("FatalException " + shopName + " 用户 " + userDO.getId() + " 的taskData有问题 errors ： " + e);
            AppInsightsUtils.trackException(e);
        }
        //获取
        return generateProgressBarVO;
    }

    public static GenerateProgressBarVO apgUserGeneratedTaskDOToGenerateProgressBarVO(APGUserGeneratedTaskDO apgUserGeneratedTaskDO, Integer totalCount, Integer unfinishedCount){
        GenerateProgressBarVO generateProgressBarVO = new GenerateProgressBarVO();
        generateProgressBarVO.setAllCount(totalCount);
        generateProgressBarVO.setUnfinishedCount(unfinishedCount);
        generateProgressBarVO.setTaskStatus(apgUserGeneratedTaskDO.getTaskStatus());
        generateProgressBarVO.setTaskModel(apgUserGeneratedTaskDO.getTaskModel());
        generateProgressBarVO.setTaskData(apgUserGeneratedTaskDO.getTaskData());
        generateProgressBarVO.setUserId(apgUserGeneratedTaskDO.getUserId());
        generateProgressBarVO.setId(apgUserGeneratedTaskDO.getId());
        return generateProgressBarVO;
    }

    @Async
    public void batchGenerateDescription(APGUsersDO usersDO, String shopName, GenerateDescriptionsVO generateDescriptionsVO) {
        //将任务id改为2
        iapgUserGeneratedTaskService.updateStatusTo2(usersDO.getId());
        //按productIds将其分为一个个小任务及传入的数据存到APG_User_Generated_SubTask中
        for (String productId : generateDescriptionsVO.getProductIds()) {
            GenerateDescriptionVO generateDescriptionVO = generateDescriptionsVOToGenerateDescriptionVO(productId, generateDescriptionsVO);
            try {
                String json = JsonUtils.OBJECT_MAPPER.writeValueAsString(generateDescriptionVO);
                //将json 存到APG_User_Generated_Subtask中
                iapgUserGeneratedSubtaskService.save(new APGUserGeneratedSubtaskDO(null, 0, json, usersDO.getId(), null));
            } catch (Exception e) {
                AppInsightsUtils.trackTrace("FatalException " + shopName + " 用户 批量翻译json化失败 errors 数据为 ： " + generateDescriptionVO + "  " + e);
//                AppInsightsUtils.trackTrace(shopName + " 用户 批量翻译json化失败 errors 数据为 ： " + generateDescriptionVO + "  " + e);
                AppInsightsUtils.trackException(e);
            }
        }

        // 发完所有productId后，插入一条发送邮件的任务
        GenerateEmailVO generateEmailVO = null;
        try {
            generateEmailVO = new GenerateEmailVO(TranslateConstants.EMAIL, generateDescriptionsVO.getProductIds());
            String email = JsonUtils.OBJECT_MAPPER.writeValueAsString(generateEmailVO);
            iapgUserGeneratedSubtaskService.save(new APGUserGeneratedSubtaskDO(null, 0, email, usersDO.getId(), null));
        } catch (JsonProcessingException e) {
            AppInsightsUtils.trackTrace(shopName + " 用户 批量翻译json化失败 errors 数据为 ： " + generateEmailVO + "  " + e);
            AppInsightsUtils.trackException(e);
//            AppInsightsUtils.trackTrace("用户 批量翻译json化失败 errors 数据为 ： " + generateEmailVO + "  " + e);
        }
    }

    private GenerateDescriptionVO generateDescriptionsVOToGenerateDescriptionVO(String productId,GenerateDescriptionsVO generateDescriptionsVO){
        GenerateDescriptionVO generateDescriptionVO = new GenerateDescriptionVO();
        generateDescriptionVO.setTemplateType(generateDescriptionsVO.getTemplateType());
        generateDescriptionVO.setTemplateId(generateDescriptionsVO.getTemplateId());
        generateDescriptionVO.setModel(generateDescriptionsVO.getModel());
        generateDescriptionVO.setLanguage(generateDescriptionsVO.getLanguage());
        generateDescriptionVO.setBrandSlogan(generateDescriptionsVO.getBrandSlogan());
        generateDescriptionVO.setBrandTone(generateDescriptionsVO.getBrandTone());
        generateDescriptionVO.setBrandWord(generateDescriptionsVO.getBrandWord());
        generateDescriptionVO.setSeoKeywords(generateDescriptionsVO.getSeoKeywords());
        generateDescriptionVO.setTextTone(generateDescriptionsVO.getTextTone());
        generateDescriptionVO.setProductId(productId);
        generateDescriptionVO.setContentType(generateDescriptionsVO.getContentType());
        generateDescriptionVO.setPageType(generateDescriptionsVO.getPageType());
        return generateDescriptionVO;
    }

    /**
     * 判断是否有任务进行
     */
    public boolean isTaskRunning(String shopName) {
        APGUsersDO userDO = iapgUsersService.getOne(new LambdaQueryWrapper<APGUsersDO>().eq(APGUsersDO::getShopName, shopName));
        if (userDO == null) {
            return false;
        }

        APGUserGeneratedTaskDO taskDO = iapgUserGeneratedTaskService.getOne(new LambdaQueryWrapper<APGUserGeneratedTaskDO>().eq(APGUserGeneratedTaskDO::getUserId, userDO.getId()));
        return taskDO.getTaskStatus() != 2;
    }

    /**
     * 用户点击暂停。
     * 以及用户卸载
     */
    public Boolean updateTaskStatusTo1(Long id) {
        //将任务改为状态改为1
        Boolean b = iapgUserGeneratedTaskService.updateStatusByUserId(id, 0);
        //将子任务改成5
        Boolean b1 = iapgUserGeneratedSubtaskService.updateAllStatusByUserId(id, 5);
        return b && b1;
    }
}
