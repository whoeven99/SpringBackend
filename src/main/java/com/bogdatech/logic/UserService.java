package com.bogdatech.logic;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.bogdatech.Service.*;
import com.bogdatech.entity.DO.EmailDO;
import com.bogdatech.entity.DO.GlossaryDO;
import com.bogdatech.entity.DO.UsersDO;
import com.bogdatech.entity.DO.WidgetConfigurationsDO;
import com.bogdatech.entity.VO.ThemeAndLanguageVO;
import com.bogdatech.entity.VO.UserInitialVO;
import com.bogdatech.enums.ErrorEnum;
import com.bogdatech.logic.redis.UserInitialRedisService;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.utils.AESUtils;
import com.bogdatech.utils.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
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
    private TencentEmailService tencentEmailService;
    @Autowired
    private IEmailService emailService;
    @Autowired
    private ITranslatesService translatesService;
    @Autowired
    private IGlossaryService iGlossaryService;
    @Autowired
    private IWidgetConfigurationsService widgetConfigurationsService;
    @Autowired
    private UserInitialRedisService userInitialRedisService;

    //添加用户
    public BaseResponse<Object> addUser(UsersDO usersDO) {
        try {
            String encryptionEmail = AESUtils.encrypt(usersDO.getEmail());
            if (encryptionEmail != null) {
                usersDO.setEncryptionEmail(encryptionEmail);
            }
        } catch (Exception e) {
            appInsights.trackTrace("FatalException addUser " + usersDO.getShopName() + " 加密失败");
        }

        int i = usersService.addUser(usersDO);
        if (i > 0) {

            //首次登陆 发送邮件
            Map<String, String> templateData = new HashMap<>();
            templateData.put("user", usersDO.getFirstName());
            Boolean flag1 = tencentEmailService.sendInitialUserEmail(137916L, templateData, FIRST_INSTALL_SUBJECT, TENCENT_FROM_EMAIL, usersDO.getEmail());

            //存数据库中
            Integer flag2 = emailService.saveEmail(new EmailDO(0, usersDO.getShopName(), TENCENT_FROM_EMAIL, usersDO.getEmail(), FIRST_INSTALL_SUBJECT, flag1 ? 1 : 0));

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

    public BaseResponse<Object> userInitialization(String shopName, UserInitialVO userInitialVO) {
        // 判断主题数据，然后判断原语言数据
        String themeName = userInitialVO.getDefaultThemeName();
        String themeId = userInitialVO.getDefaultThemeId();
        if (themeName == null || themeId == null) {
            appInsights.trackTrace("FatalException userInitialization themeData is null : " + userInitialVO);
            return new BaseResponse<>().CreateErrorResponse("Theme data is null");
        }

        // 判断redis中的数据是否改变，如果改变，发送邮件
        String userDefaultTheme = userInitialRedisService.getUserDefaultTheme(shopName);

        // redis 无值，直接写入
        if (userDefaultTheme == null) {
            userInitialRedisService.setUserDefaultTheme(shopName, themeId);
        }

        // 判断原语言数据
        String userDefaultLanguage = userInitialRedisService.getUserDefaultLanguage(shopName);
        String defaultLanguageData = userInitialVO.getDefaultLanguageData();
        if (defaultLanguageData == null) {
            return new BaseResponse<>().CreateErrorResponse("Default language data is null");
        }

        // redis 无值
        if (userDefaultLanguage == null) {
            userInitialRedisService.setUserDefaultLanguage(shopName, defaultLanguageData);
        }

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

    public BaseResponse<Object> webhookDefaultTheme(String shopName, ThemeAndLanguageVO data) {
        // 解析数据
        JsonNode jsonNode = JsonUtils.readTree(data.getThemeData());
        if (jsonNode == null) {
            return new BaseResponse<>().CreateErrorResponse("Theme data is null");
        }

        String themeName = jsonNode.get("name").asText(null);
        String themeId = jsonNode.get("admin_graphql_api_id").asText(null);
        if (themeName == null || themeId == null) {
            appInsights.trackTrace("FatalException webhookDefaultTheme themeData is null : " + data);
            return new BaseResponse<>().CreateErrorResponse("Theme data is null");
        }

        String userDefaultTheme = userInitialRedisService.getUserDefaultTheme(shopName);
        if (userDefaultTheme == null){
            return new BaseResponse<>().CreateErrorResponse("userDefaultTheme is null");
        }

        if (!themeId.equals(userDefaultTheme)) {
            // 发送主题邮件
            tencentEmailService.sendThemeEmail(shopName);

            // 修改theme为新theme
            userInitialRedisService.setUserDefaultTheme(shopName, themeId);

            // 判断switch是否创建，如果创建，再发送一封switch更换的邮件
//            if (widgetConfigurationsService.getData(shopName) != null) {
//                tencentEmailService.sendThemeSwitchEmail(shopName);
//            }
            appInsights.trackTrace("webhookDefaultTheme 用户主题改变 ： " + shopName + " name : " + themeName + " id : " + themeId );
        }

        return new BaseResponse<>().CreateSuccessResponse(true);
    }

    public BaseResponse<Object> webhookDefaultLanguage(String shopName, ThemeAndLanguageVO data) {
        // 解析数据
        JsonNode jsonNode = JsonUtils.readTree(data.getLanguageData());
        if (jsonNode == null) {
            return new BaseResponse<>().CreateErrorResponse("Language data is null");
        }

        String defaultLanguageData = jsonNode.get("primary_locale").asText(null);
        String userDefaultLanguage = userInitialRedisService.getUserDefaultLanguage(shopName);

        if (defaultLanguageData == null || userDefaultLanguage == null) {
            return new BaseResponse<>().CreateErrorResponse("Default language data is null : " + data);
        }

        // redis 无值
        if (!defaultLanguageData.equals(userDefaultLanguage)) {
            // 发送默认语言邮件
            tencentEmailService.sendDefaultLanguageEmail(shopName);
            userInitialRedisService.setUserDefaultLanguage(shopName, defaultLanguageData);
            appInsights.trackTrace("webhookDefaultLanguage 用户默认语言改变 ： " + shopName + " source : " + userDefaultLanguage + " language : " + defaultLanguageData );
        }

        return new BaseResponse<>().CreateSuccessResponse(true);
    }
}
