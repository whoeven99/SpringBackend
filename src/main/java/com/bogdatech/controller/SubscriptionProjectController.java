package com.bogdatech.controller;

import com.bogdatech.Service.ISubscriptionProjectService;
import com.bogdatech.entity.DO.SubscriptionProjectDO;
import com.bogdatech.enums.ErrorEnum;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/subscriptionProject")
public class SubscriptionProjectController {


    private final ISubscriptionProjectService subscriptionProjectService;
    @Autowired
    public SubscriptionProjectController(ISubscriptionProjectService subscriptionProjectService) {
        this.subscriptionProjectService = subscriptionProjectService;
    }

    //插入和更新SubscriptionProject
    @PostMapping("/insertOrUpdateProject")
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
    @GetMapping("/readSubscriptionProject")
    public BaseResponse<Object> readSubscriptionProject() {
        SubscriptionProjectDO[] subscriptionProjectDOS = subscriptionProjectService.readSubscriptionProject();
        if (subscriptionProjectDOS != null) {
            return new BaseResponse<>().CreateSuccessResponse(subscriptionProjectDOS);
        }else {
            return new BaseResponse<>().CreateErrorResponse(ErrorEnum.SQL_SELECT_ERROR);
        }
    }
}
