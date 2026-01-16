package com.bogda.service.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogda.service.entity.DO.RightsAndInterestsDO;
import com.bogda.service.controller.request.UserRAIRequest;
import com.bogda.service.controller.response.BaseResponse;

public interface IRightsAndInterestsService extends IService<RightsAndInterestsDO> {
    BaseResponse<Object> readRightsAndInterests();


    BaseResponse<Object> createOrUpdateRightsAndInterests(UserRAIRequest userRAIRequest);
}
