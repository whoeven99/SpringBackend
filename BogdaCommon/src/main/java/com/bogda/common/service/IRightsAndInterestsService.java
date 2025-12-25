package com.bogda.common.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogda.common.entity.DO.RightsAndInterestsDO;
import com.bogda.common.model.controller.request.UserRAIRequest;
import com.bogda.common.model.controller.response.BaseResponse;

public interface IRightsAndInterestsService extends IService<RightsAndInterestsDO> {
    BaseResponse<Object> readRightsAndInterests();


    BaseResponse<Object> createOrUpdateRightsAndInterests(UserRAIRequest userRAIRequest);
}
