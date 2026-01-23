package com.bogda.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bogda.service.Service.IAPGUserCounterService;
import com.bogda.service.Service.IAPGUserGeneratedSubtaskService;
import com.bogda.service.Service.IAPGUserPlanService;
import com.bogda.service.Service.IAPGUsersService;
import com.bogda.common.entity.DO.APGUserCounterDO;
import com.bogda.common.entity.DO.APGUserGeneratedTaskDO;
import com.bogda.common.entity.DO.APGUsersDO;
import com.bogda.common.entity.VO.GenerateDescriptionsVO;
import com.bogda.common.entity.VO.GenerateProgressBarVO;
import com.bogda.common.exception.ClientException;
import com.bogda.service.logic.APGUserGeneratedTaskService;
import com.bogda.service.logic.redis.GenerateRedisService;
import com.bogda.common.controller.response.BaseResponse;
import com.bogda.common.contants.TranslateConstants;
import com.bogda.common.utils.AppInsightsUtils;
import com.bogda.common.utils.JsonUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/apg/userGeneratedTask")
@EnableAsync
public class APGUserGeneratedTaskController {
    @Autowired
    private APGUserGeneratedTaskService apgUserGeneratedTaskService;
    @Autowired
    private IAPGUsersService iapgUsersService;
    @Autowired
    private IAPGUserPlanService iapgUserPlanService;
    @Autowired
    private IAPGUserCounterService iapgUserCounterService;
    @Autowired
    private IAPGUserGeneratedSubtaskService iapgUserGeneratedSubtaskService;
    @Autowired
    private GenerateRedisService generateRedisService;


    /**
     * 初始化或更新相关数据
     */
    @PostMapping("/initOrUpdateData")
    public BaseResponse<Object> initOrUpdateData(@RequestParam String shopName, @RequestBody APGUserGeneratedTaskDO apgUserGeneratedTaskDO) {
        Boolean result = apgUserGeneratedTaskService.initOrUpdateData(shopName, apgUserGeneratedTaskDO.getTaskStatus(), apgUserGeneratedTaskDO.getTaskModel(), null);
        if (result) {
            return new BaseResponse<>().CreateSuccessResponse(true);
        }
        return new BaseResponse<>().CreateErrorResponse(false);
    }

    /**
     * 获取用户相关数据
     * 再额外返回用户翻译总数和还未翻译数量
     */
    @GetMapping("/getUserData")
    public BaseResponse<Object> getUserData(@RequestParam String shopName) {
        GenerateProgressBarVO userData = apgUserGeneratedTaskService.getUserData(shopName);
        if (userData == null) {
            return new BaseResponse<>().CreateErrorResponse(false);
        }
        return new BaseResponse<>().CreateSuccessResponse(userData);
    }

    /**
     * 批量生成描述
     * TODO: 先简单的完成，后面再优化
     */
    @PutMapping("/batchGenerateDescription")
    public BaseResponse<Object> batchGenerateDescription(@RequestParam String shopName, @RequestBody GenerateDescriptionsVO generateDescriptionsVO) {
        //创建这样一个任务
        try {
            String json = JsonUtils.OBJECT_MAPPER.writeValueAsString(generateDescriptionsVO);
            apgUserGeneratedTaskService.initOrUpdateData(shopName, 0, generateDescriptionsVO.getPageType() + " " + generateDescriptionsVO.getContentType(), json);
        } catch (JsonProcessingException e) {
            AppInsightsUtils.trackTrace("batchGenerateDescription " + shopName + " 用户 批量生成任务失败 errors ：" + e);
            AppInsightsUtils.trackException(e);
            return new BaseResponse<>().CreateErrorResponse(false);
        }

        //判断现在是否有任务进行，有任务返回false
        if (!apgUserGeneratedTaskService.isTaskRunning(shopName)) {
            return new BaseResponse<>().CreateErrorResponse(false);
        }
        try {
            // 根据shopName获取用户数据
            APGUsersDO usersDO = iapgUsersService.getOne(new LambdaQueryWrapper<APGUsersDO>().eq(APGUsersDO::getShopName, shopName));
            //将用户暂停标志改为false
            generateRedisService.setStopFlag(usersDO.getId(), false);
            // 获取用户最大额度限制
            Integer userMaxLimit = iapgUserPlanService.getUserMaxLimit(usersDO.getId());
            //判断额度是否足够，然后决定是否继续调用
            APGUserCounterDO counterDO = iapgUserCounterService.getOne(new QueryWrapper<APGUserCounterDO>().eq("user_id", usersDO.getId()));
            if (counterDO.getUserToken() >= userMaxLimit) {
                //修改状态
                apgUserGeneratedTaskService.initOrUpdateData(shopName, 3, null, null);
                throw new ClientException(TranslateConstants.CHARACTER_LIMIT);
            }
            //将该用户的状态3，4改为9 （问题数据）
            iapgUserGeneratedSubtaskService.update34StatusTo9(usersDO.getId());
            apgUserGeneratedTaskService.batchGenerateDescription(usersDO, shopName, generateDescriptionsVO);
        } catch (ClientException e1) {
            //发送对应邮件
            //修改状态
            AppInsightsUtils.trackTrace("batchGenerateDescription " + shopName + " 用户  errors ：" + e1);
//            AppInsightsUtils.trackTrace(shopName + " 用户 batchGenerateDescription errors ：" + e1);
            AppInsightsUtils.trackException(e1);
            return BaseResponse.FailedResponse(TranslateConstants.CHARACTER_LIMIT);
        } catch (Exception e) {
            //修改状态
            //发送邮件
            AppInsightsUtils.trackTrace("FatalException batchGenerateDescription " + shopName + " 用户  errors ：" + e);
//            AppInsightsUtils.trackTrace(shopName + " 用户 batchGenerateDescription errors ：" + e);
            AppInsightsUtils.trackException(e);
            return new BaseResponse<Object>().CreateErrorResponse(false);
        }
        return new BaseResponse<Object>().CreateSuccessResponse(true);
    }

    /**
     * 查看GENERATE_SHOP里面的用户数据
     */
    @GetMapping("/getGenerateShop")
    public BaseResponse<Object> getGenerateShop() {
        return new BaseResponse<>().CreateSuccessResponse(generateRedisService.getGenerateShop());
    }

    /**
     * 删除Generate_shop里面的用户数据
     */
    @GetMapping("/deleteGenerateShop")
    public BaseResponse<Object> deleteGenerateShop(@RequestParam String shopName) {
        //根据shopName，获取userId
        APGUsersDO usersDO = iapgUsersService.getOne(new QueryWrapper<APGUsersDO>().eq("shop_name", shopName));
        generateRedisService.releaseGenerateShop(usersDO.getId());
        return new BaseResponse<>().CreateSuccessResponse(true);
    }

    /**
     * 停止用户批量翻译
     */
    @PutMapping("/stopBatchGenerateDescription")
    public BaseResponse<Object> stopBatchGenerateDescription(@RequestParam String shopName) {
        //根据shopName，获取userId
        APGUsersDO usersDO = iapgUsersService.getOne(new LambdaQueryWrapper<APGUsersDO>().eq(APGUsersDO::getShopName, shopName));
        Boolean result = generateRedisService.setStopFlag(usersDO.getId(), true);
        AppInsightsUtils.trackTrace("stopBatchGenerateDescription " + shopName + " 停止翻译标识 : " + result);
        //将任务和子任务的状态改为1
        Boolean updateFlag = apgUserGeneratedTaskService.updateTaskStatusTo1(usersDO.getId());
        if (updateFlag) {
            return new BaseResponse<>().CreateSuccessResponse(true);
        } else {
            return new BaseResponse<>().CreateErrorResponse(false);
        }
    }

    /**
     * 查看用户停止状态
     */
    @GetMapping("/getStopFlag")
    public BaseResponse<Object> getStopFlag() {
        return new BaseResponse<>().CreateSuccessResponse(generateRedisService.getStopFlags());
    }
}
