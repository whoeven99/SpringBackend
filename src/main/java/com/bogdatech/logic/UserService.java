package com.bogdatech.logic;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.bogdatech.Service.*;
import com.bogdatech.entity.DO.*;
import com.bogdatech.enums.ErrorEnum;
import com.bogdatech.integration.EmailIntegration;
import com.bogdatech.model.controller.request.TencentSendEmailRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.utils.AESUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashMap;
import java.util.Map;
import static com.bogdatech.constants.MailChimpConstants.*;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

@Component
@Transactional
public class UserService {
    @Autowired
    private IUsersService usersService;
    @Autowired
    private ITranslationCounterService translationCounterService;
    @Autowired
    private IAILanguagePacksService aiLanguagePacksService;
    @Autowired
    private IUserSubscriptionsService userSubscriptionsService;
    @Autowired
    private EmailIntegration emailIntegration;
    @Autowired
    private IEmailService emailServicel;
    @Autowired
    private ITranslatesService translatesService;
    @Autowired
    private IGlossaryService iGlossaryService;
    @Autowired
    private IWidgetConfigurationsService widgetConfigurationsService;

    //添加用户
    public BaseResponse<Object> addUser(UsersDO usersDO) {
        try {
            String encryptionEmail = AESUtils.encrypt(usersDO.getEmail());
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
        int MAX_RETRY_ATTEMPTS = 3;
        long RETRY_DELAY_MS = 1000;
        int attempt = 0;
        while (attempt < MAX_RETRY_ATTEMPTS) {
            try {
                String shopName = userRequest.getShopName();
                appInsights.trackTrace("unInstallApp " + shopName + " 用户卸载应用");

                // 更改用户卸载翻译时间
                usersService.unInstallApp(userRequest);

                // 将用户定时任务的true都改为false
                translatesService.updateAutoTranslateByShopNameToFalse(shopName);

                // 将用户翻译状态改为0
                translatesService.updateAllStatusTo0(shopName);

                // 将用户ip关掉
                // 将词汇表改为0
                iGlossaryService.update(new UpdateWrapper<GlossaryDO>().eq("shop_name", shopName).set("status", 0));
                widgetConfigurationsService.update(new UpdateWrapper<WidgetConfigurationsDO>().eq("shop_name", shopName).set("ip_open", false));

                // 获取用户订单表里计划为Active的订单
                // 删除对应的额度
                // 获取用户额度数据，判断是否是免费试用卸载，然后扣额度
                translationCounterService.deleteTrialCounter(shopName);

                return true;
            } catch (Exception e) {
                attempt++;
                if (attempt >= MAX_RETRY_ATTEMPTS) {
                    appInsights.trackTrace("Uninstallation failed, retrying..." + e);
                    return false;
                }
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    appInsights.trackTrace("Retry delay interrupted for shop: " + ie);
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
            encryptionEmail = AESUtils.encrypt(usersDO.getEmail());
        } catch (Exception e) {
            appInsights.trackTrace("getEncryptedEmail " + shopName + "加密邮箱失败");
        }

        //更新加密邮箱
        usersService.update(new UpdateWrapper<UsersDO>()
                .eq("shop_name", shopName)
                .set("encryption_email", encryptionEmail));
        //返回对应的值
        return encryptionEmail;
    }
}
