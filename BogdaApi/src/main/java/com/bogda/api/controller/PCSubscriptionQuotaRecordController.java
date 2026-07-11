package com.bogda.api.controller;

import com.bogda.common.controller.response.BaseResponse;
import com.bogda.repository.entity.PCSubscriptionQuotaRecordDO;
import org.springframework.web.bind.annotation.*;

import static com.bogda.api.support.DisabledProductEndpoints.error;

@RestController
@RequestMapping("/pc/subscriptionQuotaRecord")
public class PCSubscriptionQuotaRecordController {

    @PutMapping("/addSubscriptionQuotaRecord")
    public BaseResponse<Object> addSubscriptionQuotaRecord(
            @RequestParam String shopName,
            @RequestBody PCSubscriptionQuotaRecordDO pcSubscriptionQuotaRecordDO) {
        return error();
    }
}
