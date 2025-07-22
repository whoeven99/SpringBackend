package com.bogdatech.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bogdatech.Service.IAPGUsersService;
import com.bogdatech.entity.DO.APGUserGeneratedTaskDO;
import com.bogdatech.entity.DO.APGUsersDO;
import com.bogdatech.entity.VO.GenerateDescriptionVO;
import com.bogdatech.entity.VO.GenerateDescriptionsVO;
import com.bogdatech.logic.APGUserGeneratedTaskService;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/apg/userGeneratedTask")
public class APGUserGeneratedTaskController {
    private final APGUserGeneratedTaskService apgUserGeneratedTaskService;

    @Autowired
    public APGUserGeneratedTaskController(APGUserGeneratedTaskService apgUserGeneratedTaskService) {
        this.apgUserGeneratedTaskService = apgUserGeneratedTaskService;
    }

    /**
     * 初始化或更新相关数据
     * */
    @PostMapping("/initOrUpdateData")
    public BaseResponse<Object> initOrUpdateData(@RequestParam String shopName, @RequestBody APGUserGeneratedTaskDO apgUserGeneratedTaskDO){
        Boolean result = apgUserGeneratedTaskService.initOrUpdateData(shopName, apgUserGeneratedTaskDO.getTaskStatus(), apgUserGeneratedTaskDO.getTaskModel());
        if (result) {
            return new BaseResponse<>().CreateSuccessResponse(true);
        }
        return new BaseResponse<>().CreateErrorResponse(false);
    }

    /**
     * 获取用户相关数据
     * */
    @GetMapping("/getUserData")
    public BaseResponse<Object> getUserData(@RequestParam String shopName){
        APGUserGeneratedTaskDO userData = apgUserGeneratedTaskService.getUserData(shopName);
        if (userData == null ) {
            return new BaseResponse<>().CreateErrorResponse(false);
        }
        return new BaseResponse<>().CreateSuccessResponse(userData);
    }

    /**
     * 批量生成描述
     * */
    @PutMapping("/batchGenerateDescription")
    public BaseResponse<Object> batchGenerateDescription(@RequestParam String shopName, @RequestBody GenerateDescriptionsVO generateDescriptionsVO){
        apgUserGeneratedTaskService.batchGenerateDescription(shopName, generateDescriptionsVO);

        return null;
    }
}
