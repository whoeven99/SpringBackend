package com.bogda.api.controller;


import com.bogda.common.entity.DO.UsersDO;
import com.bogda.common.entity.VO.ThemeAndLanguageVO;
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

    // 用户初始化检测
    @GetMapping("/InitializationDetection")
    public BaseResponse<Object> initializationDetection(@RequestParam String shopName) {
        return new BaseResponse<>().CreateSuccessResponse(userService.InitializationDetection(shopName));
    }

    // 修改用户订阅计划
    @PostMapping("/checkUserPlan")
    public BaseResponse<Object> checkUserPlan(@RequestBody UserSubscriptionsRequest userSubscriptionsRequest) {
        return new BaseResponse<>().CreateSuccessResponse(userService.checkUserPlan(userSubscriptionsRequest.getShopName(), userSubscriptionsRequest.getPlanId()));
    }

    // webhook 回调默认theme是否需要发送邮件
    @PostMapping("/webhookDefaultTheme")
    public BaseResponse<Object> webhookDefaultTheme(@RequestParam String shopName, @RequestBody ThemeAndLanguageVO data) {
        return userService.webhookDefaultTheme(shopName, data);
    }

    // webhook 回调默认语言是否需要发送邮件
    @PostMapping("/webhookDefaultLanguage")
    public BaseResponse<Object> webhookDefaultLanguage(@RequestParam String shopName, @RequestBody ThemeAndLanguageVO data) {
        return userService.webhookDefaultLanguage(shopName, data);
    }
}
