package com.bogda.service.logic.PCApp;

import com.bogda.common.controller.response.BaseResponse;
import com.bogda.repository.entity.PCSubscriptionQuotaRecordDO;
import com.bogda.repository.repo.PCSubscriptionQuotaRecordRepo;
import com.bogda.common.utils.AppInsightsUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;



@Component
public class PCSubscriptionQuotaRecordService {
    @Autowired
    private PCSubscriptionQuotaRecordRepo pcSubscriptionQuotaRecordRepo;

    public BaseResponse<Object> addSubscriptionQuotaRecord(String shopName, PCSubscriptionQuotaRecordDO pcSubscriptionQuotaRecordDO) {

        //如果数据库中含有这条数据，就不插入了
        PCSubscriptionQuotaRecordDO quotaRecordDO = pcSubscriptionQuotaRecordRepo.getQuotaRecordBySubscriptionIdAndBillingCycle(pcSubscriptionQuotaRecordDO.getSubscriptionId(), 1);

        AppInsightsUtils.trackTrace("addSubscriptionQuotaRecord " + pcSubscriptionQuotaRecordDO.getSubscriptionId() + " 这条数据只是判断是否要往数据库存值 quotaRecordDO: " + quotaRecordDO);
        if (quotaRecordDO != null) {
            AppInsightsUtils.trackTrace("addSubscriptionQuotaRecord " + pcSubscriptionQuotaRecordDO.getSubscriptionId() + " 数据库中含有这条数据，就不插入了");
            return new BaseResponse<>().CreateSuccessResponse("not need insert");
        }

        boolean insert = pcSubscriptionQuotaRecordRepo.insertQuotaRecord(pcSubscriptionQuotaRecordDO.getSubscriptionId(), 1);
        if (insert) {
            return new BaseResponse<>().CreateSuccessResponse(true);
        }
        return new BaseResponse<>().CreateSuccessResponse("insert error");
    }
}
