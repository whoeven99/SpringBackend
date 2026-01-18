package com.bogda.web.controller;

import com.bogda.common.entity.DO.UserTypeTokenDO;
import com.bogda.service.logic.UserTypeTokenService;
import com.bogda.common.controller.request.TranslateRequest;
import com.bogda.common.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/userTypeToken")
public class UserTypeTokenController {
    @Autowired
    private UserTypeTokenService userTypeTokenService;

    //获取用户所有模块的token
    @PostMapping("/getUserToken")
    public BaseResponse<Object> getUserToken(@RequestBody TranslateRequest request){
        UserTypeTokenDO userTypeToken = userTypeTokenService.getUserTypeToken(request);
        if (userTypeToken == null) {
            return new BaseResponse<>().CreateErrorResponse(request);
        }else {
            return new BaseResponse<>().CreateSuccessResponse(userTypeToken);
        }
    }

    //获取用户的初始token
    @PostMapping("/getUserInitToken")
    public void getUserInitToken(@RequestBody TranslateRequest request){
        //查询数据库是否要
        userTypeTokenService.getUserInitToken(request);
    }

    //根据shopName从数据库获取获取初始值
    @PostMapping("/getUserInitTokenByShopName")
    public BaseResponse<Object> getUserInitTokenByShopName(@RequestBody TranslateRequest request){
        UserTypeTokenDO userTypeToken = userTypeTokenService.getUserInitTokenByShopName(request.getShopName());
        if (userTypeToken != null) {
            return new BaseResponse<>().CreateSuccessResponse(userTypeToken);
        }else {
            return new BaseResponse<>().CreateErrorResponse(request);
        }
    }

    //异步调用startTokenCount方法获取所有的数据信息
    @PostMapping("/startTokenCount")
    public void startTokenCount(@RequestBody TranslateRequest request) {
        userTypeTokenService.startTokenCount(request);
    }
 }
