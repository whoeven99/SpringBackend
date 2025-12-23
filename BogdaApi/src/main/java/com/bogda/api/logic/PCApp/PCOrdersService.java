package com.bogda.api.logic.PCApp;

import com.bogda.api.model.controller.response.BaseResponse;
import com.bogda.api.repository.entity.PCOrdersDO;
import com.bogda.api.repository.repo.PCOrdersRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PCOrdersService {
    @Autowired
    private PCOrdersRepo pcOrdersRepo;

    public BaseResponse<Object> insertOrUpdateOrder(PCOrdersDO pcOrdersDO) {
        PCOrdersDO selectOrder = pcOrdersRepo.getOrderByOrderId(pcOrdersDO.getOrderId());
        boolean result;

        if (selectOrder == null) {
            // 新增
            result = pcOrdersRepo.save(pcOrdersDO);
            return result
                    ? new BaseResponse<>().CreateSuccessResponse(true)
                    : new BaseResponse<>().CreateErrorResponse("save error");
        } else {
            // 更新
            result = pcOrdersRepo.updateStatusByShopName(selectOrder.getId(), pcOrdersDO.getStatus());
            return result
                    ? new BaseResponse<>().CreateSuccessResponse(true)
                    : new BaseResponse<>().CreateErrorResponse("update error");
        }
    }

    public BaseResponse<Object> getLatestActiveSubscribeId(String shopName) {
        String latestActiveSubscribeId = pcOrdersRepo.getLatestActiveSubscribeId(shopName);
        if (latestActiveSubscribeId != null){
            return new BaseResponse<>().CreateSuccessResponse(latestActiveSubscribeId);
        }
        return new BaseResponse<>().CreateErrorResponse("no active subscribe");
    }
}
