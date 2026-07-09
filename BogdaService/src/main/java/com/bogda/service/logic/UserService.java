package com.bogda.service.logic;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.bogda.common.reporter.TraceReporterHolder;
import com.bogda.service.Service.*;
import com.bogda.common.entity.DO.EmailDO;
import com.bogda.common.entity.DO.UsersDO;
import com.bogda.common.entity.VO.UserInitialVO;
import com.bogda.common.contants.MailChimpConstants;
import com.bogda.common.enums.ErrorEnum;
import com.bogda.common.controller.response.BaseResponse;
import com.bogda.common.utils.AESUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Component
@Transactional
public class UserService {
    @Autowired
    private IUsersService usersService;
    @Autowired
    private ITranslationCounterService translationCounterService;
    @Autowired
    private IUserSubscriptionsService userSubscriptionsService;
    @Autowired
    private TencentEmailService tencentEmailService;
    @Autowired
    private IEmailService emailService;
    @Autowired
    private ITranslatesService translatesService;

    //添加用户
    public BaseResponse<Object> addUser(UsersDO usersDO) {
        try {
            String encryptionEmail = AESUtils.encrypt(usersDO.getEmail());
            if (encryptionEmail != null) {
                usersDO.setEncryptionEmail(encryptionEmail);
            }
        } catch (Exception e) {
            TraceReporterHolder.report("UserService.addUser", "FatalException addUser " + usersDO.getShopName() + " 加密失败");
        }

        int i = usersService.addUser(usersDO);
        if (i > 0) {

            //首次登陆 发送邮件
            Map<String, String> templateData = new HashMap<>();
            templateData.put("user", usersDO.getFirstName());
            Boolean flag1 = tencentEmailService.sendInitialUserEmail(137916L, templateData,
                    MailChimpConstants.FIRST_INSTALL_SUBJECT, MailChimpConstants.TENCENT_FROM_EMAIL, usersDO.getEmail());

            //存数据库中
            Integer flag2 = emailService.saveEmail(new EmailDO(0, usersDO.getShopName(),
                    MailChimpConstants.TENCENT_FROM_EMAIL, usersDO.getEmail(), MailChimpConstants.FIRST_INSTALL_SUBJECT, flag1 ? 1 : 0));

            if (flag2 > 0 && flag1) {
                return new BaseResponse<>().CreateSuccessResponse(true);
            } else {
                return new BaseResponse<>().CreateErrorResponse(MailChimpConstants.TENCENT_SEND_FAILED);
            }

        } else {
            return new BaseResponse<>().CreateErrorResponse(ErrorEnum.SQL_INSERT_ERROR);
        }

    }

    //获取用户信息
    public UsersDO getUser(String shopName) {
        //更新用户登陆时间
        usersService.updateUserLoginTime(shopName);
        return usersService.getUserByName(shopName);
    }

    //用户卸载应用
    public Boolean unInstallApp(UsersDO userRequest) {
        int MAX_RETRY_ATTEMPTS = 3;
        long RETRY_DELAY_MS = 1000;
        int attempt = 0;
        while (attempt < MAX_RETRY_ATTEMPTS) {
            try {
                String shopName = userRequest.getShopName();
                TraceReporterHolder.report("UserService.unInstallApp", "unInstallApp " + shopName + " 用户卸载应用");

                // 更改用户卸载翻译时间
                usersService.unInstallApp(userRequest);

                // 将用户定时任务的true都改为false
                translatesService.updateAutoTranslateByShopNameToFalse(shopName);

                // 将用户翻译状态改为0
                translatesService.updateAllStatusTo0(shopName);

                // 获取用户订单表里计划为Active的订单
                // 删除对应的额度
                // 获取用户额度数据，判断是否是免费试用卸载，然后扣额度
                translationCounterService.deleteTrialCounter(shopName);

                return true;
            } catch (Exception e) {
                attempt++;
                if (attempt >= MAX_RETRY_ATTEMPTS) {
                    TraceReporterHolder.report("UserService.unInstallApp", "FatalException Uninstallation failed, retrying..." + e);
                    return false;
                }
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    TraceReporterHolder.report("UserService.unInstallApp", "FatalException Retry delay interrupted for shop: " + ie);
                    Thread.currentThread().interrupt(); // Restore interrupted status
                    return false;
                }
            }
        }
        return false;
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

        //查询是否添加免费额度
        if (translationCounterService.getMaxCharsByShopName(shopName) != null) {
            map.put("insertCharsByShopName", true);
        } else {
            map.put("insertCharsByShopName", false);
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

    /**
     * 只读判定该 shop 是否属于老用户系统，供 TSF 新用户系统在安装时做账本路由判定。
     * 不产生任何副作用（不建用户、不更新登录时间）。
     * legacy = 已存在于 Users 表 或 有过订阅记录。
     */
    public Map<String, Boolean> checkUserExists(String shopName) {
        Map<String, Boolean> map = new HashMap<>();
        boolean exists = usersService.getUserByName(shopName) != null;
        boolean hasSubscription = userSubscriptionsService.getDataByShopName(shopName) != null;
        map.put("exists", exists);
        map.put("hasSubscription", hasSubscription);
        map.put("legacy", exists || hasSubscription);
        return map;
    }

    public Integer checkUserPlan(String shopName, int planId) {
        return userSubscriptionsService.checkUserPlan(shopName, planId);
    }

    public BaseResponse<Object> userInitialization(String shopName, UserInitialVO userInitialVO) {
        userInitialVO.setShopName(shopName);
        if (getUser(shopName) == null) {
            UsersDO userRequest = new UsersDO();
            userRequest.setShopName(shopName);
            userRequest.setAccessToken(userInitialVO.getAccessToken());
            userRequest.setUserTag(userInitialVO.getUserTag());
            userRequest.setFirstName(userInitialVO.getFirstName());
            userRequest.setLastName(userInitialVO.getLastName());
            userRequest.setEmail(userInitialVO.getEmail());
            return addUser(userRequest);
        }

        // 更新user表里面的token
        updateUserTokenByShopName(shopName, userInitialVO.getAccessToken());
        return new BaseResponse<>().CreateSuccessResponse(true);
    }
}
