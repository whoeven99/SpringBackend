package com.bogdatech.logic;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.bogdatech.Service.*;
import com.bogdatech.entity.EmailDO;
import com.bogdatech.entity.UsersDO;
import com.bogdatech.enums.ErrorEnum;
import com.bogdatech.integration.EmailIntegration;
import com.bogdatech.model.controller.request.LoginAndUninstallRequest;
import com.bogdatech.model.controller.request.TencentSendEmailRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.bogdatech.constants.MailChimpConstants.*;
import static com.bogdatech.utils.AESUtils.encrypt;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

@Component
@Transactional
public class UserService {

    private final IUsersService usersService;
    private final TaskScheduler taskScheduler;
    private final ITranslationCounterService translationCounterService;
    private final IAILanguagePacksService aiLanguagePacksService;
    private final IUserSubscriptionsService userSubscriptionsService;
    private final EmailIntegration emailIntegration;
    private final IEmailService emailServicel;
    AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<>();

    @Autowired
    public UserService(IUsersService usersService, TaskScheduler taskScheduler, ITranslationCounterService translationCounterService, IAILanguagePacksService aiLanguagePacksService, IUserSubscriptionsService userSubscriptionsService, EmailIntegration emailIntegration, IEmailService emailServicel) {
        this.usersService = usersService;
        this.taskScheduler = taskScheduler;
        this.translationCounterService = translationCounterService;
        this.aiLanguagePacksService = aiLanguagePacksService;
        this.userSubscriptionsService = userSubscriptionsService;
        this.emailIntegration = emailIntegration;
        this.emailServicel = emailServicel;
    }

    //添加用户
    public BaseResponse<Object> addUser(UsersDO usersDO) {
        try {
            String encryptionEmail = encrypt(usersDO.getEmail());
            if (encryptionEmail != null) {
                usersDO.setEncryptionEmail(encryptionEmail);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        int i = usersService.addUser(usersDO);
        if (i > 0) {

            //首次登陆 发送邮件
            Map<String, String> templateData = new HashMap<>();
            templateData.put("user", usersDO.getFirstName());
            Boolean flag1 = emailIntegration.sendEmailByTencent(
                    new TencentSendEmailRequest(137916L, templateData, FIRST_INSTALL_SUBJECT, TENCENT_FROM_EMAIL, usersDO.getEmail()));

            //存数据库中
            Integer flag2 = emailServicel.saveEmail(new EmailDO(0, usersDO.getShopName(), TENCENT_FROM_EMAIL, usersDO.getEmail(), FIRST_INSTALL_SUBJECT, flag1 ? 1 : 0));

            if (flag2 > 0 && flag1) {
                return new BaseResponse<>().CreateSuccessResponse(true);
            } else {
                return new BaseResponse<>().CreateErrorResponse(TENCENT_SEND_FAILED);
            }

        } else {
            return new BaseResponse<>().CreateErrorResponse(ErrorEnum.SQL_INSERT_ERROR);
        }

    }

    //获取用户信息
    public UsersDO getUser(UsersDO request) {
        //更新用户登陆时间
        usersService.updateUserLoginTime(request.getShopName());
        return usersService.getUserByName(request.getShopName());
    }

    //用户卸载应用
    public Boolean unInstallApp(UsersDO userRequest) {
        boolean success = false;
        while (!success) {
            try {
                usersService.unInstallApp(userRequest);
                success = true;  // 如果没有抛出异常，则表示执行成功，退出循环
            } catch (Exception e) {
                appInsights.trackTrace("Uninstallation failed, retrying...");
            }
        }
        return true;
    }

    //用户卸载应用后48小时后清除数据
    @Async
    public void cleanData(UsersDO userRequest) {
        //TODO: 测试 这一部分有问题
        System.out.println("开始执行定时任务： " + userRequest.getShopName());
        String shopName = userRequest.getShopName();
//        long delayMillis = TimeUnit.HOURS.toMillis(48); // 48小时转为毫秒
        long delayMillis = TimeUnit.SECONDS.toMillis(10);
        // 创建一个触发器，在48小时后执行
        Trigger trigger = context -> {
            Instant executionTime = Instant.now().plusMillis(delayMillis); // 当前时间 + 48小时
            return Date.from(executionTime).toInstant(); // 将 Instant 转换为 Date
        };

        // 使用 taskScheduler 来调度任务
        futureRef.set(taskScheduler.schedule(() -> {
            // 执行任务
            judgeUserLogin(shopName);
            // 任务执行完后，取消任务
            ScheduledFuture<?> future = futureRef.get();
            if (future != null) {
                future.cancel(true);  // 取消任务
            }
        }, trigger));

//        judgeUserLogin(userRequest.getShopName());
    }

    // 判断48小时后 用户是否再次登陆过 如果登陆就不删除了，如果没登陆就删除数据
    public void judgeUserLogin(String shopName) {
        //获取用户的登陆时间
        LoginAndUninstallRequest loginAndUninstallRequest = usersService.getUserLoginTime(shopName);
        //当登陆时间 > 卸载时间时，什么都不做； 当登陆时间 < 卸载时间时，删除数据
//        loginAndUninstallRequest.getLoginTime();
        if (loginAndUninstallRequest.getLoginTime().before(loginAndUninstallRequest.getUninstallTime())) {
            deleteUserData(shopName);
        }
    }

    public void deleteUserData(String shopName) {
        appInsights.trackTrace("删除数据: " + shopName);
//        usersService.deleteUserGlossaryData(shopName);
//        usersService.deleteCurrenciesData(shopName);
//        usersService.deleteTranslatesData(shopName);
//        appInsights.trackTrace("删除数据完成: " + shopName);

    }

    public Boolean requestData() {
        return true;
    }

    public Boolean deleteData() {
        return true;
    }

    //检测以上四个接口返回值是否符合预期，符合预期则返回{`${接口名}`:true ...}，后续前端根据返回值调用对应的接口，其中语言数据也会在返回值中存在false值集体调用，正常情况下都是经过webhook通知后调用
    public Map<String, Boolean> InitializationDetection(String shopName) {
        Map<String, Boolean> map = new HashMap<>();
        //查询用户是否初始化
//        map.put("add", false);


        //查询是否添加免费额度
        if (translationCounterService.getMaxCharsByShopName(shopName) != null) {
            map.put("insertCharsByShopName", true);
        } else {
            map.put("insertCharsByShopName", false);
        }

        //查询是否添加默认语言包
        if (aiLanguagePacksService.getPackIdByShopName(shopName) != null) {
            map.put("addDefaultLanguagePack", true);
        } else {
            map.put("addDefaultLanguagePack", false);
        }

        //查询是否添加用户付费计划
        if (userSubscriptionsService.getUserSubscriptionPlan(shopName) != null) {
            map.put("addUserSubscriptionPlan", true);
        } else {
            map.put("addUserSubscriptionPlan", false);
        }
        return map;
    }

    public void updateUserTokenByShopName(String shopName, String accessToken) {
        usersService.updateUserTokenByShopName(shopName, accessToken);
    }

    public Integer checkUserPlan(String shopName, int planId) {
        return userSubscriptionsService.checkUserPlan(shopName, planId);
    }

    public String getEncryptedEmail(String shopName) {
        UsersDO usersDO = usersService.getOne(new QueryWrapper<UsersDO>()
                .eq("shop_name", shopName)
        );
        if (usersDO.getEncryptionEmail() != null) {
            return usersDO.getEncryptionEmail();
        }
        String encryptionEmail = null;
        try {
            encryptionEmail = encrypt(usersDO.getEmail());
        } catch (Exception e) {
            appInsights.trackTrace(shopName + "加密邮箱失败");
        }

        //更新加密邮箱
        usersService.update(new UpdateWrapper<UsersDO>()
                .eq("shop_name", shopName)
                .set("encryption_email", encryptionEmail));
        //返回对应的值
        return encryptionEmail;
    }
}
