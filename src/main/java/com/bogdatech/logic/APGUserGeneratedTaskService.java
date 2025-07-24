package com.bogdatech.logic;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bogdatech.Service.*;
import com.bogdatech.entity.DO.APGUserCounterDO;
import com.bogdatech.entity.DO.APGUserGeneratedSubtaskDO;
import com.bogdatech.entity.DO.APGUserGeneratedTaskDO;
import com.bogdatech.entity.DO.APGUsersDO;
import com.bogdatech.entity.VO.GenerateDescriptionVO;
import com.bogdatech.entity.VO.GenerateDescriptionsVO;
import com.bogdatech.entity.VO.GenerateEmailVO;
import com.bogdatech.entity.VO.GenerateProgressBarVO;
import com.bogdatech.exception.ClientException;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Arrays;

import static com.bogdatech.constants.TranslateConstants.CHARACTER_LIMIT;
import static com.bogdatech.constants.TranslateConstants.EMAIL;
import static com.bogdatech.logic.TranslateService.OBJECT_MAPPER;
import static com.bogdatech.task.GenerateDbTask.GENERATE_SHOP_BAR;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.TypeConversionUtils.apgUserGeneratedTaskDOToGenerateProgressBarVO;
import static com.bogdatech.utils.TypeConversionUtils.generateDescriptionsVOToGenerateDescriptionVO;

@Service
public class APGUserGeneratedTaskService {
    private final IAPGUsersService iapgUsersService;
    private final IAPGUserGeneratedTaskService iapgUserGeneratedTaskService;
    private final IAPGUserPlanService iapgUserPlanService;
    private final IAPGUserCounterService iapgUserCounterService;
    private final IAPGUserGeneratedSubtaskService iapgUserGeneratedSubtaskService;

    @Autowired
    public APGUserGeneratedTaskService(IAPGUsersService iapgUsersService, IAPGUserGeneratedTaskService iapgUserGeneratedTaskService, IAPGUserPlanService iapgUserPlanService, IAPGUserCounterService iapgUserCounterService, IAPGUserGeneratedSubtaskService iapgUserGeneratedSubtaskService) {
        this.iapgUsersService = iapgUsersService;
        this.iapgUserGeneratedTaskService = iapgUserGeneratedTaskService;
        this.iapgUserPlanService = iapgUserPlanService;
        this.iapgUserCounterService = iapgUserCounterService;
        this.iapgUserGeneratedSubtaskService = iapgUserGeneratedSubtaskService;
    }

    /**
     * 初始化或更新相关数据
     * */
    public Boolean initOrUpdateData(String shopName, Integer status, String taskModel, String taskData){
        APGUsersDO userDO = iapgUsersService.getOne(new LambdaQueryWrapper<APGUsersDO>().eq(APGUsersDO::getShopName, shopName));
        if (userDO == null) {
            return false;
        }

        //获取用户任务状态
        APGUserGeneratedTaskDO taskDO = iapgUserGeneratedTaskService.getOne(new LambdaQueryWrapper<APGUserGeneratedTaskDO>().eq(APGUserGeneratedTaskDO::getUserId, userDO.getId()));
        APGUserGeneratedTaskDO apgUserGeneratedTaskDO = new APGUserGeneratedTaskDO(null, userDO.getId(), null ,taskModel, taskData);
        if (taskDO == null){
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
        try {
            GenerateDescriptionsVO generateDescriptionsVO = OBJECT_MAPPER.readValue(taskDO.getTaskData(), GenerateDescriptionsVO.class);
            Integer totalCount = generateDescriptionsVO.getProductIds().length;
            Integer unfinishedCount = iapgUserGeneratedSubtaskService.list(new LambdaQueryWrapper<APGUserGeneratedSubtaskDO>()
                    .in(APGUserGeneratedSubtaskDO::getStatus, Arrays.asList(0, 3, 4))
                    .eq(APGUserGeneratedSubtaskDO::getUserId, userDO.getId())).size();
            generateProgressBarVO = apgUserGeneratedTaskDOToGenerateProgressBarVO(taskDO, totalCount, unfinishedCount);
            generateProgressBarVO.setProductTitle(GENERATE_SHOP_BAR.get(userDO.getId()));
            //获取产品标题
            return generateProgressBarVO;
        } catch (Exception e) {
            appInsights.trackTrace(shopName + " 用户 " + userDO.getId() + " 的taskData有问题 errors ： " + e);
        }
        //获取
        return generateProgressBarVO;
    }

    /**
     * 对批量生成的异常捕捉处理
     * */
    @Async
    public void batchGenerateDescriptionException(String shopName, GenerateDescriptionsVO generateDescriptionsVO){
        try {
            batchGenerateDescription(shopName, generateDescriptionsVO);
        }catch (ClientException e1){
            //发送对应邮件
            //修改状态
            appInsights.trackTrace(shopName + " 用户 batchGenerateDescription errors ：" + e1);
            System.out.println(shopName + " 用户 batchGenerateDescription errors ：" + e1);
            appInsights.trackException(e1);
        } catch (Exception e) {
            //修改状态
            //发送邮件
            appInsights.trackTrace(shopName + " 用户 batchGenerateDescription errors ：" + e);
            System.out.println(shopName + " 用户 batchGenerateDescription errors ：" + e);
            appInsights.trackException(e);
        }
    }

    public void batchGenerateDescription(String shopName, GenerateDescriptionsVO generateDescriptionsVO) {
        // 根据shopName获取用户数据
        APGUsersDO usersDO = iapgUsersService.getOne(new LambdaQueryWrapper<APGUsersDO>().eq(APGUsersDO::getShopName, shopName));
        // 获取用户最大额度限制
        Integer userMaxLimit = iapgUserPlanService.getUserMaxLimit(usersDO.getId());
        //判断额度是否足够，然后决定是否继续调用
        APGUserCounterDO counterDO = iapgUserCounterService.getOne(new QueryWrapper<APGUserCounterDO>().eq("user_id", usersDO.getId()));
        if (counterDO.getUserToken() >= userMaxLimit) {
            //修改状态
            initOrUpdateData(shopName, 3, null, null);
            throw new ClientException(CHARACTER_LIMIT);
        }

        //按productIds将其分为一个个小任务及传入的数据存到APG_User_Generated_SubTask中
        for (String productId : generateDescriptionsVO.getProductIds()) {
            GenerateDescriptionVO generateDescriptionVO = generateDescriptionsVOToGenerateDescriptionVO(productId, generateDescriptionsVO);
            try {
                String json = OBJECT_MAPPER.writeValueAsString(generateDescriptionVO);
                //将json 存到APG_User_Generated_Subtask中
                iapgUserGeneratedSubtaskService.save(new APGUserGeneratedSubtaskDO(null, 0, json, usersDO.getId(), null));
            } catch (Exception e) {
                appInsights.trackTrace(shopName + " 用户 批量翻译json化失败 errors 数据为 ： " + generateDescriptionVO + "  " + e);
                System.out.println(shopName + " 用户 批量翻译json化失败 errors 数据为 ： " + generateDescriptionVO + "  " + e);
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
            System.out.println("用户 批量翻译json化失败 errors 数据为 ： " + generateEmailVO + "  " + e);
        }
    }

    /**
     * 判断是否有任务进行
     * */
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
     * */
    public Boolean updateTaskStatusTo1(Long id) {
        //将任务改为状态改为1
        Boolean b = iapgUserGeneratedTaskService.updateStatusByUserId(id, 1);
        //将子任务改成5
        Boolean b1 = iapgUserGeneratedSubtaskService.updateAllStatusByUserId(id, 5);
        return b && b1;
    }
}
