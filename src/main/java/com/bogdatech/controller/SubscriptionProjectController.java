package com.bogdatech.controller;

import com.bogdatech.Service.ISubscriptionProjectService;
import com.bogdatech.entity.SubscriptionProjectDO;
import com.bogdatech.enums.ErrorEnum;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SubscriptionProjectController {

    @Autowired
    private ISubscriptionProjectService subscriptionProjectService;
    // TODO:计划项目的CRUD

    //插入和更新SubscriptionProject
    @PostMapping("/subscriptionProject/insertOrUpdateProject")
    public BaseResponse<Object> insertOrUpdateProject(SubscriptionProjectDO subscriptionProjectDO) {
        SubscriptionProjectDO byId = subscriptionProjectService.getById(subscriptionProjectDO.getProjectId());
        if (byId == null) {
            Boolean b = subscriptionProjectService.insertSubscriptionProjectDO(subscriptionProjectDO);
            if (b) {
                return new BaseResponse<>().CreateSuccessResponse(200);
            }else {
                return new BaseResponse<>().CreateErrorResponse(ErrorEnum.SQL_INSERT_ERROR);
            }
        } else {
            boolean b = subscriptionProjectService.updateById(subscriptionProjectDO);
            if (b) {
                return new BaseResponse<>().CreateSuccessResponse(200);
            }else {
                return new BaseResponse<>().CreateErrorResponse(ErrorEnum.SQL_UPDATE_ERROR);
            }
        }
    }

    //获得SubscriptionProject所有的数据
    @GetMapping("/subscriptionProject/readSubscriptionProject")
    public BaseResponse<Object> readSubscriptionProject() {
        SubscriptionProjectDO[] subscriptionProjectDOS = subscriptionProjectService.readSubscriptionProject();
        if (subscriptionProjectDOS != null) {
            return new BaseResponse<>().CreateSuccessResponse(subscriptionProjectDOS);
        }else {
            return new BaseResponse<>().CreateErrorResponse(ErrorEnum.SQL_SELECT_ERROR);
        }
    }
}
