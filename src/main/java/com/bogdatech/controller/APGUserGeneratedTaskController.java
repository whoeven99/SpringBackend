package com.bogdatech.controller;

import com.azure.core.annotation.Put;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bogdatech.Service.IAPGUsersService;
import com.bogdatech.entity.DO.APGUserGeneratedTaskDO;
import com.bogdatech.entity.DO.APGUsersDO;
import com.bogdatech.entity.VO.GenerateDescriptionsVO;
import com.bogdatech.entity.VO.GenerateProgressBarVO;
import com.bogdatech.logic.APGUserGeneratedTaskService;
import com.bogdatech.model.controller.response.BaseResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.bind.annotation.*;

import static com.bogdatech.logic.TranslateService.OBJECT_MAPPER;
import static com.bogdatech.task.GenerateDbTask.GENERATE_SHOP;
import static com.bogdatech.task.GenerateDbTask.GENERATE_SHOP_STOP_FLAG;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

@RestController
@RequestMapping("/apg/userGeneratedTask")
@EnableAsync
public class APGUserGeneratedTaskController {
    private final APGUserGeneratedTaskService apgUserGeneratedTaskService;
    private final IAPGUsersService iapgUsersService;

    @Autowired
    public APGUserGeneratedTaskController(APGUserGeneratedTaskService apgUserGeneratedTaskService, IAPGUsersService iapgUsersService) {
        this.apgUserGeneratedTaskService = apgUserGeneratedTaskService;
        this.iapgUsersService = iapgUsersService;
    }

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
     */
    @PutMapping("/batchGenerateDescription")
    public BaseResponse<Object> batchGenerateDescription(@RequestParam String shopName, @RequestBody GenerateDescriptionsVO generateDescriptionsVO) {
        //创建这样一个任务
        try {
            String json = OBJECT_MAPPER.writeValueAsString(generateDescriptionsVO);
            apgUserGeneratedTaskService.initOrUpdateData(shopName, null, generateDescriptionsVO.getPageType() + " " + generateDescriptionsVO.getContentType(), json);
        } catch (JsonProcessingException e) {
            appInsights.trackTrace(shopName + " 用户 batchGenerateDescription errors ：" + e);
            System.out.println(shopName + " 用户 batchGenerateDescription errors ：" + e);
            appInsights.trackException(e);
            return new BaseResponse<>().CreateErrorResponse(false);
        }

        //判断现在是否有任务进行，有任务返回false
        if (!apgUserGeneratedTaskService.isTaskRunning(shopName)) {
            return new BaseResponse<>().CreateErrorResponse(false);
        }
        apgUserGeneratedTaskService.batchGenerateDescriptionException(shopName, generateDescriptionsVO);
        return new BaseResponse<>().CreateSuccessResponse(true);
    }

    /**
     * 查看GENERATE_SHOP里面的用户数据
     * */
    @GetMapping("/getGenerateShop")
    public BaseResponse<Object> getGenerateShop() {
        return new BaseResponse<>().CreateSuccessResponse(GENERATE_SHOP);
    }

    /**
     * 删除Generate_shop里面的用户数据
     * */
    @GetMapping("/deleteGenerateShop")
    public BaseResponse<Object> deleteGenerateShop(@RequestParam String shopName) {
        //根据shopName，获取userId
        APGUsersDO usersDO = iapgUsersService.getOne(new QueryWrapper<APGUsersDO>().eq("shop_name", shopName));
        GENERATE_SHOP.remove(usersDO.getId());
        return new BaseResponse<>().CreateSuccessResponse(true);
    }

    /**
     * 停止用户批量翻译
     * */
    @PutMapping("/stopBatchGenerateDescription")
    public BaseResponse<Object> stopBatchGenerateDescription(@RequestParam String shopName) {
        //根据shopName，获取userId
        APGUsersDO usersDO = iapgUsersService.getOne(new LambdaQueryWrapper<APGUsersDO>().eq(APGUsersDO::getShopName, shopName));
        Boolean result = GENERATE_SHOP_STOP_FLAG.put(usersDO.getId(), true);
        //将任务和子任务的状态改为1
        Boolean updateFlag = apgUserGeneratedTaskService.updateTaskStatusTo1(usersDO.getId());
        if (Boolean.TRUE.equals(result) && Boolean.TRUE.equals(updateFlag)) {
            return new BaseResponse<>().CreateSuccessResponse(true);
        }else {
            return new BaseResponse<>().CreateErrorResponse(false);
        }
    }

    /**
     * 查看用户停止状态
     * */
    @GetMapping("/getStopFlag")
    public BaseResponse<Object> getStopFlag() {
        return new BaseResponse<>().CreateSuccessResponse(GENERATE_SHOP_STOP_FLAG);
    }
}
