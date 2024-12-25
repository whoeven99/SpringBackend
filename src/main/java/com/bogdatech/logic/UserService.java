package com.bogdatech.logic;

import com.bogdatech.Service.IUsersService;
import com.bogdatech.entity.UsersDO;
import com.bogdatech.enums.ErrorEnum;
import com.bogdatech.model.controller.request.LoginAndUninstallRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import com.microsoft.applicationinsights.TelemetryClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Component
@Transactional
public class UserService {

    private final IUsersService usersService;
    private final KlaviyoService klaviyoService;
    private final TaskScheduler taskScheduler;
    TelemetryClient appInsights = new TelemetryClient();
    AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<>();
    @Autowired
    public UserService(IUsersService usersService, KlaviyoService klaviyoService, TaskScheduler taskScheduler) {
        this.usersService = usersService;
        this.klaviyoService = klaviyoService;
        this.taskScheduler = taskScheduler;
    }

    public BaseResponse<Object> addUser(UsersDO usersDO) {
        int i = usersService.addUser(usersDO);
        if (i > 0) {
            //TODO: 发送邮件
            return new BaseResponse<>().CreateSuccessResponse(true);
        } else {
            return new BaseResponse<>().CreateErrorResponse(ErrorEnum.SQL_INSERT_ERROR);
        }

    }

    public UsersDO getUser(UsersDO request) {
        //更新用户登陆时间
        usersService.updateUserLoginTime(request.getShopName());
        return usersService.getUserByName(request.getShopName());
    }

    //添加User
    @Async
    public void addUserAsync(UsersDO userRequest) {
        if (getUser(userRequest) == null) {
            addUser(userRequest);
        }
    }

    //用户卸载应用
    public Boolean unInstallApp(UsersDO userRequest) {
        boolean success = false;
        while (!success) {
            try {
                usersService.unInstallApp(userRequest);
                success = true;  // 如果没有抛出异常，则表示执行成功，退出循环
            } catch (Exception e) {
                System.out.println("Uninstallation failed, retrying...");
            }
        }
        return true;
    }

    //用户卸载应用后48小时后清除数据
    public void cleanData(UsersDO userRequest) {
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
            judgeUserLogin(userRequest.getShopName());
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
        if (loginAndUninstallRequest.getLoginTime().before(loginAndUninstallRequest.getUninstallTime())){
            deleteUserData(shopName);
        }
    }
    public void deleteUserData(String shopName){
        appInsights.trackTrace("删除数据: " + shopName);
        usersService.deleteUserGlossaryData(shopName);
        usersService.deleteCurrenciesData(shopName);
        usersService.deleteTranslatesData(shopName);
        appInsights.trackTrace("删除数据完成: " + shopName);

    }
    public Boolean requestData() {
        return true;
    }

    public Boolean deleteData() {
        return true;
    }
}
