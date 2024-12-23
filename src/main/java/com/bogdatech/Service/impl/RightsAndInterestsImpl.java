package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.IRightsAndInterestsService;
import com.bogdatech.entity.RightsAndInterestsDO;
import com.bogdatech.entity.UserRightsAndInterestsDO;
import com.bogdatech.mapper.RightsAndInterestsMapper;
import com.bogdatech.model.controller.request.UserRAIRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.stereotype.Service;

@Service
public class RightsAndInterestsImpl extends ServiceImpl<RightsAndInterestsMapper, RightsAndInterestsDO> implements IRightsAndInterestsService {
    @Override
    public BaseResponse<Object> readRightsAndInterests() {
        RightsAndInterestsDO[] rightsAndInterestsDOS = baseMapper.readRightsAndInterests();
        if (rightsAndInterestsDOS.length == 0) {
            return new BaseResponse<>().CreateErrorResponse("No rights and interests found");
        }
        return new BaseResponse<>().CreateSuccessResponse(rightsAndInterestsDOS);
    }

    @Override
    public BaseResponse<Object> createOrUpdateRightsAndInterests(UserRAIRequest userRAIRequest) {
        // 判断该对应映射关系是否存在
        //从数据库中根据shopName获取user的id
        Integer userId = baseMapper.getUserIdByShopName(userRAIRequest.getShopName());
        //从数据库中获取对应关系
        UserRightsAndInterestsDO userRightsAndInterestsDO = baseMapper.getUserRightsAndInterests(userId, userRAIRequest.getRightsAndInterestsId());
        if (userRightsAndInterestsDO != null){
            //如果存在，则直接返回
            return new BaseResponse<>().CreateSuccessResponse("You already own this benefit.");
        }else {
            //如果不存在，则创建
            baseMapper.addUserRightsAndInterests(userId, userRAIRequest.getRightsAndInterestsId());
            return new BaseResponse<>().CreateSuccessResponse(new UserRightsAndInterestsDO(userId, userRAIRequest.getRightsAndInterestsId()));
        }
    }
}
