package com.bogdatech.controller;


import com.bogdatech.entity.UsersDO;
import com.bogdatech.logic.UserService;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
public class UserController {
    private final UserService userService;
    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

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
            return new BaseResponse<>().CreateErrorResponse("User already exists");
        }
//        userService.addUserAsync(userRequest);
    }

    //用户卸载应用
    @DeleteMapping("/uninstall")
    public BaseResponse<Object> uninstallApp(@RequestBody UsersDO userRequest) {
        //当卸载时，更新卸载时间
        return new BaseResponse<>().CreateSuccessResponse(userService.unInstallApp(userRequest));
    }

    //用户卸载应用后48小时后清除数据
    @DeleteMapping("/cleanData")
    public BaseResponse<Object> cleanData(@RequestBody UsersDO userRequest) {
        userService.cleanData(userRequest);
        return new BaseResponse<>().CreateSuccessResponse(200);
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
}