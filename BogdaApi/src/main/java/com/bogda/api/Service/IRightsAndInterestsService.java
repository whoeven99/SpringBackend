package com.bogda.api.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogda.api.entity.DO.RightsAndInterestsDO;
import com.bogda.api.model.controller.request.UserRAIRequest;
import com.bogda.api.model.controller.response.BaseResponse;

public interface IRightsAndInterestsService extends IService<RightsAndInterestsDO> {
    BaseResponse<Object> readRightsAndInterests();


    BaseResponse<Object> createOrUpdateRightsAndInterests(UserRAIRequest userRAIRequest);
}
