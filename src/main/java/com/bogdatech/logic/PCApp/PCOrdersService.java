package com.bogdatech.logic.PCApp;

import com.bogdatech.entity.DO.CharsOrdersDO;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.repository.entity.PCOrdersDO;
import com.bogdatech.repository.repo.PCOrdersServiceRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PCOrdersService {
    @Autowired
    private PCOrdersServiceRepo pcOrdersServiceRepo;

    public BaseResponse<Object> insertOrUpdateOrder(PCOrdersDO pcOrdersDO) {
        PCOrdersDO selectOrder = pcOrdersServiceRepo.getOrderByOrderId(pcOrdersDO.getOrderId());
        boolean result;

        if (selectOrder == null) {
            // 新增
            result = pcOrdersServiceRepo.save(pcOrdersDO);
            return result
                    ? new BaseResponse<>().CreateSuccessResponse(true)
                    : new BaseResponse<>().CreateErrorResponse("save error");
        } else {
            // 更新
            result = pcOrdersServiceRepo.updateStatusByShopName(selectOrder.getId(), pcOrdersDO.getStatus());
            return result
                    ? new BaseResponse<>().CreateSuccessResponse(true)
                    : new BaseResponse<>().CreateErrorResponse("update error");
        }
    }

    public BaseResponse<Object> getLatestActiveSubscribeId(String shopName) {
        String latestActiveSubscribeId = pcOrdersServiceRepo.getLatestActiveSubscribeId(shopName);
        if (latestActiveSubscribeId != null){
            return new BaseResponse<>().CreateSuccessResponse(latestActiveSubscribeId);
        }
        return new BaseResponse<>().CreateErrorResponse("no active subscribe");
    }
}
