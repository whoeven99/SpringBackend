package com.bogda.api.controller;


import com.bogda.common.entity.DO.UsersDO;
import com.bogda.common.entity.VO.UserInitialVO;
import com.bogda.service.logic.TranslateService;
import com.bogda.service.logic.UserService;
import com.bogda.common.controller.request.UserSubscriptionsRequest;
import com.bogda.common.controller.response.BaseResponse;
import com.bogda.common.reporter.TraceReporterHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/user")
public class UserController {
    @Autowired
    private UserService userService;
    @Autowired
    private TranslateService translateService;

    // 用户初始化
    @PostMapping("/userInitialization")
    public BaseResponse<Object> userInitialization(@RequestParam String shopName, @RequestBody UserInitialVO userInitialVO) {
        TraceReporterHolder.report("UserController.userInitialization", "userInitialization userInitialVO : " + userInitialVO);
        return userService.userInitialization(shopName, userInitialVO);
    }

    // 用户卸载应用
    @DeleteMapping("/uninstall")
    public BaseResponse<Object> uninstallApp(@RequestBody UsersDO userRequest) {
        translateService.stopTranslationManually(userRequest.getShopName());
        return new BaseResponse<>().CreateSuccessResponse(userService.unInstallApp(userRequest));
    }

    // 客户可以向店主请求其数据
    @PostMapping("/requestData")
    public BaseResponse<Object> requestData() {
        return new BaseResponse<>().CreateSuccessResponse(userService.requestData());
    }

    // 店主可以代表客户请求删除数据
    @DeleteMapping("/deleteData")
    public BaseResponse<Object> deleteData() {
        return new BaseResponse<>().CreateSuccessResponse(userService.deleteData());
    }

    // 用户初始化检测
    @GetMapping("/InitializationDetection")
    public BaseResponse<Object> initializationDetection(@RequestParam String shopName) {
        return new BaseResponse<>().CreateSuccessResponse(userService.InitializationDetection(shopName));
    }

    // 只读判定 shop 是否为老用户系统用户（供 TSF 新用户系统安装时做账本路由判定，无副作用）
    @GetMapping("/exists")
    public BaseResponse<Object> exists(@RequestParam String shopName) {
        return new BaseResponse<>().CreateSuccessResponse(userService.checkUserExists(shopName));
    }

    // 修改用户订阅计划
    @PostMapping("/checkUserPlan")
    public BaseResponse<Object> checkUserPlan(@RequestBody UserSubscriptionsRequest userSubscriptionsRequest) {
        return new BaseResponse<>().CreateSuccessResponse(userService.checkUserPlan(userSubscriptionsRequest.getShopName(), userSubscriptionsRequest.getPlanId()));
    }
}
