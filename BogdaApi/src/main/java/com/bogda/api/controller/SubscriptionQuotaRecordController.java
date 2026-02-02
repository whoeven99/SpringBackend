package com.bogda.api.controller;

import com.bogda.common.controller.response.BaseResponse;
import com.bogda.repository.entity.SubscriptionQuotaRecordDO;
import com.bogda.service.logic.SubscriptionQuotaRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/subscriptionQuotaRecord")
public class SubscriptionQuotaRecordController {
    @Autowired
    private SubscriptionQuotaRecordService subscriptionQuotaRecordService;

    //在购买订阅之后，给用户添加对应的订阅信息
    @PutMapping("/addSubscriptionQuotaRecord")
    public BaseResponse<Object> addSubscriptionQuotaRecord(@RequestBody SubscriptionQuotaRecordDO subscriptionQuotaRecordDO) {
        return subscriptionQuotaRecordService.addSubscriptionQuotaRecord(subscriptionQuotaRecordDO);
    }
}
