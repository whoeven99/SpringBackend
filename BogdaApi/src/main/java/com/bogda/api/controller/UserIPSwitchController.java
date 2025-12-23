package com.bogda.api.controller;

import com.bogda.api.Service.IUserIPSwitchService;
import com.bogda.api.entity.DO.UserIPSwitchDO;
import com.bogda.api.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import static com.bogda.api.utils.CaseSensitiveUtils.appInsights;

@RestController
@RequestMapping("/IpSwitch")
public class UserIPSwitchController {
    @Autowired
    private IUserIPSwitchService userIPSwitchService;

    @PostMapping("/insertSwitch")
    public BaseResponse<Object> insertSwitch(@RequestBody UserIPSwitchDO userIPSwitchDO) {

        int i = userIPSwitchService.insertSwitch(userIPSwitchDO);
        if (i > 0 ) {
            return new BaseResponse<>().CreateSuccessResponse(userIPSwitchDO.getSwitchId());
        }else {
            return new BaseResponse<>().CreateErrorResponse(String.valueOf(userIPSwitchDO.getSwitchId()));
        }
    }

    @GetMapping("/getSwitchId")
    public BaseResponse<Object> getSwitchId(String shopName) {
        try {
            return new BaseResponse<>().CreateSuccessResponse(userIPSwitchService.getSwitchId(shopName));
        } catch (Exception e) {
            appInsights.trackException(e);
            appInsights.trackTrace("getSwitchId error: " + e.getMessage());
        }
        return new BaseResponse<>().CreateErrorResponse("fail");
    }

}
