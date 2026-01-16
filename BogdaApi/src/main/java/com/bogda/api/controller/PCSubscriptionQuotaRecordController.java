package com.bogda.api.controller;

import com.bogda.service.logic.PCApp.PCSubscriptionQuotaRecordService;
import com.bogda.service.controller.response.BaseResponse;
import com.bogda.repository.entity.PCSubscriptionQuotaRecordDO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/pc/subscriptionQuotaRecord")
public class PCSubscriptionQuotaRecordController {
    @Autowired
    private PCSubscriptionQuotaRecordService pcSubscriptionQuotaRecordService;

    //在购买订阅之后，给用户添加对应的订阅信息
    @PutMapping("/addSubscriptionQuotaRecord")
    public BaseResponse<Object> addSubscriptionQuotaRecord(@RequestParam String shopName, @RequestBody PCSubscriptionQuotaRecordDO pcSubscriptionQuotaRecordDO) {
        return pcSubscriptionQuotaRecordService.addSubscriptionQuotaRecord(shopName, pcSubscriptionQuotaRecordDO);
    }
}
