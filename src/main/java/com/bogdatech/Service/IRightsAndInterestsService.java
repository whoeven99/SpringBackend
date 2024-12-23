package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.RightsAndInterestsDO;
import com.bogdatech.model.controller.request.UserRAIRequest;
import com.bogdatech.model.controller.response.BaseResponse;

public interface IRightsAndInterestsService extends IService<RightsAndInterestsDO> {
    BaseResponse<Object> readRightsAndInterests();


    BaseResponse<Object> createOrUpdateRightsAndInterests(UserRAIRequest userRAIRequest);
}
