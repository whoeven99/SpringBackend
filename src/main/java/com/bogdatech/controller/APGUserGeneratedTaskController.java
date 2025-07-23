package com.bogdatech.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bogdatech.Service.IAPGUsersService;
import com.bogdatech.entity.DO.APGUserGeneratedTaskDO;
import com.bogdatech.entity.DO.APGUsersDO;
import com.bogdatech.entity.VO.GenerateDescriptionVO;
import com.bogdatech.entity.VO.GenerateDescriptionsVO;
import com.bogdatech.exception.ClientException;
import com.bogdatech.logic.APGUserGeneratedTaskService;
import com.bogdatech.model.controller.response.BaseResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.bind.annotation.*;

import static com.bogdatech.constants.TranslateConstants.CHARACTER_LIMIT;
import static com.bogdatech.logic.TranslateService.OBJECT_MAPPER;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

@RestController
@RequestMapping("/apg/userGeneratedTask")
@EnableAsync
public class APGUserGeneratedTaskController {
    private final APGUserGeneratedTaskService apgUserGeneratedTaskService;

    @Autowired
    public APGUserGeneratedTaskController(APGUserGeneratedTaskService apgUserGeneratedTaskService) {
        this.apgUserGeneratedTaskService = apgUserGeneratedTaskService;
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
     */
    @GetMapping("/getUserData")
    public BaseResponse<Object> getUserData(@RequestParam String shopName) {
        APGUserGeneratedTaskDO userData = apgUserGeneratedTaskService.getUserData(shopName);
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
}
