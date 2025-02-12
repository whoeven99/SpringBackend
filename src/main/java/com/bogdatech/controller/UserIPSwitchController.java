package com.bogdatech.controller;

import com.bogdatech.Service.IUserIPSwitchService;
import com.bogdatech.entity.UserIPSwitchDO;
import com.bogdatech.model.controller.response.BaseResponse;
import com.microsoft.applicationinsights.TelemetryClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/IpSwitch")
public class UserIPSwitchController {
    private final IUserIPSwitchService userIPSwitchService;

    @Autowired
    public UserIPSwitchController(IUserIPSwitchService userIPSwitchService) {
        this.userIPSwitchService = userIPSwitchService;
    }
    private final TelemetryClient appInsights = new TelemetryClient();

    @PostMapping("/insertSwitch")
    public BaseResponse<Object> insertSwitch(@RequestBody UserIPSwitchDO userIPSwitchDO) {

        int i = userIPSwitchService.insertSwitch(userIPSwitchDO);
        if (i > 0 ) {
            return new BaseResponse<>().CreateSuccessResponse(i);
        }else {
            return new BaseResponse<>().CreateErrorResponse(String.valueOf(i));
        }
    }

    @GetMapping("/getSwitchId")
    public BaseResponse<Object> getSwitchId(String shopName) {
        try {
            return new BaseResponse<>().CreateSuccessResponse(userIPSwitchService.getSwitchId(shopName));
        } catch (Exception e) {
            System.out.println("getSwitchId error: " + e.getMessage());
//            appInsights.trackTrace("getSwitchId error: " + e.getMessage());
        }
        return new BaseResponse<>().CreateErrorResponse("fail");
    }

}
