package com.bogdatech.controller;

import com.bogdatech.entity.UserTypeTokenDO;
import com.bogdatech.logic.UserTypeTokenService;
import com.bogdatech.model.controller.request.TranslateRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/userTypeToken")
public class UserTypeTokenController {
    private final UserTypeTokenService userTypeTokenService;

    @Autowired
    public UserTypeTokenController(UserTypeTokenService userTypeTokenService) {
        this.userTypeTokenService = userTypeTokenService;
    }

    @PostMapping("/getUserToken")
    public BaseResponse<Object> getUserToken(@RequestBody TranslateRequest request){
        UserTypeTokenDO userTypeToken = userTypeTokenService.getUserTypeToken(request);
        if (userTypeToken == null) {
            return new BaseResponse<>().CreateErrorResponse(request);
        }else {
            return new BaseResponse<>().CreateSuccessResponse(userTypeToken);
        }
    }
}
