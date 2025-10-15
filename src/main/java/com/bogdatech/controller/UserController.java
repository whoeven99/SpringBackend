package com.bogdatech.controller;


import com.bogdatech.entity.DO.UsersDO;
import com.bogdatech.logic.TranslateService;
import com.bogdatech.logic.UserService;
import com.bogdatech.model.controller.request.UserSubscriptionsRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/user")
public class UserController {
    @Autowired
    private UserService userService;
    @Autowired
    private TranslateService translateService;

    //获得用户数据
    @GetMapping("/get")
    public UsersDO getUser(@RequestBody UsersDO userRequest) {
        return userService.getUser(userRequest);
    }

    // 添加用户
    @PostMapping("/add")
    public BaseResponse<Object> addUser(@RequestBody UsersDO userRequest) {

        if (userService.getUser(userRequest) == null) {
            return userService.addUser(userRequest);
        }else {
            //更新user表里面的token
            userService.updateUserTokenByShopName(userRequest.getShopName(), userRequest.getAccessToken());
            return new BaseResponse<>().CreateErrorResponse("User already exists");
        }
//        userService.addUserAsync(userRequest);
    }

    //用户卸载应用
    @DeleteMapping("/uninstall")
    public BaseResponse<Object> uninstallApp(@RequestBody UsersDO userRequest) {
        //卸载时，停止翻译
        translateService.stopTranslationManually(userRequest.getShopName());
        //当卸载时，更新卸载时间
        return new BaseResponse<>().CreateSuccessResponse(userService.unInstallApp(userRequest));
    }

    //客户可以向店主请求其数据
    @PostMapping("/requestData")
    public BaseResponse<Object> requestData() {
        return new BaseResponse<>().CreateSuccessResponse(userService.requestData());
    }

    //店主可以代表客户请求删除数据
    @DeleteMapping("/deleteData")
    public BaseResponse<Object> deleteData() {
        return new BaseResponse<>().CreateSuccessResponse(userService.deleteData());
    }

    //用户初始化检测
    @GetMapping("/InitializationDetection")
    public BaseResponse<Object> initializationDetection(@RequestParam String shopName) {
        return new BaseResponse<>().CreateSuccessResponse(userService.InitializationDetection(shopName));
    }

    //修改用户订阅计划
    @PostMapping("/checkUserPlan")
    public BaseResponse<Object> checkUserPlan(@RequestBody UserSubscriptionsRequest userSubscriptionsRequest) {
        return new BaseResponse<>().CreateSuccessResponse(userService.checkUserPlan(userSubscriptionsRequest.getShopName(), userSubscriptionsRequest.getPlanId()));
    }

    //获取用户表中的加密邮件
    @PostMapping("/getEncryptedEmail")
    public BaseResponse<Object> getEncryptedEmail(@RequestBody UsersDO userRequest) {
        return new BaseResponse<>().CreateSuccessResponse(userService.getEncryptedEmail(userRequest.getShopName()));
    }

}