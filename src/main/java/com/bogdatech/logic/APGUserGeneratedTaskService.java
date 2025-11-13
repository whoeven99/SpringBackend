package com.bogdatech.logic;

import com.bogdatech.Service.IAPGUserGeneratedSubtaskService;
import com.bogdatech.Service.IAPGUserGeneratedTaskService;
import com.bogdatech.Service.IAPGUsersService;
import com.bogdatech.entity.DO.APGUserGeneratedSubtaskDO;
import com.bogdatech.entity.DO.APGUserGeneratedTaskDO;
import com.bogdatech.entity.DO.APGUsersDO;
import com.bogdatech.entity.VO.GenerateDescriptionVO;
import com.bogdatech.entity.VO.GenerateDescriptionsVO;
import com.bogdatech.entity.VO.GenerateEmailVO;
import com.bogdatech.entity.VO.GenerateProgressBarVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

import static com.bogdatech.constants.TranslateConstants.EMAIL;
import static com.bogdatech.logic.TranslateService.OBJECT_MAPPER;
import static com.bogdatech.task.GenerateDbTask.GENERATE_SHOP_BAR;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.TypeConversionUtils.apgUserGeneratedTaskDOToGenerateProgressBarVO;
import static com.bogdatech.utils.TypeConversionUtils.generateDescriptionsVOToGenerateDescriptionVO;

@Component
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
        APGUsersDO userDO = iapgUsersService.getUserByShopName(shopName);
        if (userDO == null) {
            return false;
        }

        //获取用户任务状态
        APGUserGeneratedTaskDO taskDO = iapgUserGeneratedTaskService.getTaskByUserId(userDO.getId());
        APGUserGeneratedTaskDO apgUserGeneratedTaskDO = new APGUserGeneratedTaskDO(null, userDO.getId(), null, taskModel, taskData, null);
        if (taskDO == null) {
            //插入对应数据
            return iapgUserGeneratedTaskService.save(apgUserGeneratedTaskDO);
        }

        //更新对应数据
        apgUserGeneratedTaskDO.setTaskStatus(status);
        apgUserGeneratedTaskDO.setTaskData(taskData);
        return iapgUserGeneratedTaskService.updateTaskByUserId(apgUserGeneratedTaskDO, userDO.getId());

    }

    public GenerateProgressBarVO getUserData(String shopName) {
        APGUsersDO userDO = iapgUsersService.getUserByShopName(shopName);

        if (userDO == null) {
            return null;
        }

        GenerateProgressBarVO generateProgressBarVO = new GenerateProgressBarVO();
        APGUserGeneratedTaskDO taskDO = iapgUserGeneratedTaskService.getTaskByUserId(userDO.getId());

        if (taskDO == null) {
            return generateProgressBarVO;
        }

        try {
            GenerateDescriptionsVO generateDescriptionsVO = OBJECT_MAPPER.readValue(taskDO.getTaskData(), GenerateDescriptionsVO.class);
            Integer totalCount = generateDescriptionsVO.getProductIds().length;
            Integer unfinishedCount = iapgUserGeneratedSubtaskService.getUnfinishedByStatusAndUserId(Arrays.asList(0, 3, 4)
                    , userDO.getId()).size();
            generateProgressBarVO = apgUserGeneratedTaskDOToGenerateProgressBarVO(taskDO, totalCount, unfinishedCount);
            generateProgressBarVO.setProductTitle(GENERATE_SHOP_BAR.get(userDO.getId()));
            generateProgressBarVO.setStatus(GENERATE_STATE_BAR.get(userDO.getId()));
            generateProgressBarVO.setTaskTime(taskDO.getUpdateTime());//获取对应的时间
            //获取产品标题
            return generateProgressBarVO;
        } catch (Exception e) {
            appInsights.trackTrace(shopName + " 用户 " + userDO.getId() + " 的taskData有问题 errors ： " + e);
            appInsights.trackException(e);
        }
        //获取
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
                String json = OBJECT_MAPPER.writeValueAsString(generateDescriptionVO);
                //将json 存到APG_User_Generated_Subtask中
                iapgUserGeneratedSubtaskService.save(new APGUserGeneratedSubtaskDO(null, 0, json, usersDO.getId(), null));
            } catch (Exception e) {
                appInsights.trackTrace(shopName + " 用户 批量翻译json化失败 errors 数据为 ： " + generateDescriptionVO + "  " + e);
//                appInsights.trackTrace(shopName + " 用户 批量翻译json化失败 errors 数据为 ： " + generateDescriptionVO + "  " + e);
                appInsights.trackException(e);
            }
        }

        // 发完所有productId后，插入一条发送邮件的任务
        GenerateEmailVO generateEmailVO = null;
        try {
            generateEmailVO = new GenerateEmailVO(EMAIL, generateDescriptionsVO.getProductIds());
            String email = OBJECT_MAPPER.writeValueAsString(generateEmailVO);
            iapgUserGeneratedSubtaskService.save(new APGUserGeneratedSubtaskDO(null, 0, email, usersDO.getId(), null));
        } catch (JsonProcessingException e) {
            appInsights.trackTrace(shopName + " 用户 批量翻译json化失败 errors 数据为 ： " + generateEmailVO + "  " + e);
            appInsights.trackException(e);
//            appInsights.trackTrace("用户 批量翻译json化失败 errors 数据为 ： " + generateEmailVO + "  " + e);
        }
    }

    /**
     * 判断是否有任务进行
     */
    public boolean isTaskRunning(String shopName) {
        APGUsersDO userDO = iapgUsersService.getUserByShopName(shopName);
        if (userDO == null) {
            return false;
        }

        APGUserGeneratedTaskDO taskDO = iapgUserGeneratedTaskService.getTaskByUserId(userDO.getId());
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
