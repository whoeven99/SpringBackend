package com.bogda.service.logic;

import com.bogda.service.Service.*;
import com.bogda.service.utils.CurrencyConfig;
import com.bogda.service.entity.DO.*;
import com.bogda.service.integration.EmailIntegration;
import com.bogda.service.logic.redis.TranslateTaskMonitorV2RedisService;
import com.bogda.service.controller.request.TencentSendEmailRequest;
import com.bogda.repository.entity.InitialTaskV2DO;
import com.bogda.service.utils.ModuleCodeUtils;
import com.bogda.common.contants.MailChimpConstants;
import com.bogda.common.utils.AppInsightsUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import static com.bogda.service.utils.StringUtils.parseShopName;

@Component
public class TencentEmailService {
    @Autowired
    private EmailIntegration emailIntegration;
    @Autowired
    private IEmailService emailService;
    @Autowired
    private IUsersService usersService;
    @Autowired
    private ITranslationCounterService translationCounterService;
    @Autowired
    private IAPGEmailService iapgEmailService;
    @Autowired
    private TranslateTaskMonitorV2RedisService translateTaskMonitorV2RedisService;

    /**
     * 发送IP请求即将不足的邮件
     */
    public Boolean sendEmailByIpRunningOut(String shopName) {
        UsersDO userByName = usersService.getUserByName(shopName);
        String name = parseShopName(shopName);
        Map<String, String> templateData = new HashMap<>();
        templateData.put("user", userByName.getFirstName());
        templateData.put("shop_name", name);
        return emailIntegration.sendEmailByTencent(
                new TencentSendEmailRequest(141470L, templateData, MailChimpConstants.EMAIL_IP_RUNNING_OUT, MailChimpConstants.TENCENT_FROM_EMAIL, userByName.getEmail()));
    }

    /**
     * 发送IP请求不足的邮件
     */
    public Boolean sendEmailByIpOut(String shopName) {
        UsersDO userByName = usersService.getUserByName(shopName);
        String name = parseShopName(shopName);
        Map<String, String> templateData = new HashMap<>();
        templateData.put("user", userByName.getFirstName());
        templateData.put("shop_name", name);
        return emailIntegration.sendEmailByTencent(
                new TencentSendEmailRequest(141471L, templateData, MailChimpConstants.EMAIL_IP_OUT, MailChimpConstants.TENCENT_FROM_EMAIL, userByName.getEmail()));
    }

    public boolean sendAutoTranslateEmail(String shopName, List<InitialTaskV2DO> shopTasks) {
        String name = parseShopName(shopName);

        UsersDO usersDO = usersService.getUserByName(shopName);
        Map<String, String> templateData = new HashMap<>();
        templateData.put("user", usersDO.getFirstName());
        templateData.put("shop_name", name);

        StringBuilder divBuilder = new StringBuilder();
        for (InitialTaskV2DO shopTask : shopTasks) {
            if (shopTask.getUsedToken().equals(0)) {
                continue;
            }
            divBuilder.append("<div class=\"language-block\">");
            divBuilder.append("<h4>").append(shopTask.getTarget()).append("</h4>");
            divBuilder.append("<ul>");
            divBuilder.append("<li><span>Credits Used:</span> ").append(shopTask.getUsedToken()).append(" credits used").append("</li>");
            divBuilder.append("<li><span>Translation Time:</span> ").append(shopTask.getTranslationMinutes()).append(" minutes").append("</li>");
            divBuilder.append("</ul>");
            divBuilder.append("</div>");
        }

        // 都continue了
        if (divBuilder.toString().isEmpty()) {
            AppInsightsUtils.trackTrace("sendAutoTranslateEmail divBuilder is empty " + shopName);
            return true;
        }
        templateData.put("html_data", String.valueOf(divBuilder));
        return emailIntegration.sendEmailByTencent(140352L, MailChimpConstants.SUCCESSFUL_AUTO_TRANSLATION_SUBJECT,
                templateData, MailChimpConstants.TENCENT_FROM_EMAIL, usersDO.getEmail());
    }

    public boolean sendSuccessEmail(String shopName, String target, Integer translateTime, Integer usedTokenByTask, Integer usedToken, Integer totalToken) {
        // 发送的具体内容
        Map<String, String> templateData = new HashMap<>();
        setCommonTemplate(templateData, shopName, target, translateTime, usedTokenByTask);

        UsersDO usersDO = usersService.getUserByName(shopName);
        templateData.put("user", usersDO.getFirstName());

        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.US);
        templateData.put("remaining_credits", totalToken < usedToken ? "0" : formatter.format(totalToken - usedToken));

        return emailIntegration.sendEmailByTencent(137353L, MailChimpConstants.SUCCESSFUL_TRANSLATION_SUBJECT,
                templateData, MailChimpConstants.TENCENT_FROM_EMAIL, usersDO.getEmail());
    }

    public boolean sendFailedEmail(String shopName, String target, Integer translateTime, Integer usedTokenByTask, String translatedModules, String unTranslatedModules) {
        // 发送的具体内容
        Map<String, String> templateData = new HashMap<>();
        setCommonTemplate(templateData, shopName, target, translateTime, usedTokenByTask);

        UsersDO usersDO = usersService.getUserByName(shopName);
        templateData.put("user", usersDO.getFirstName());

        // 获取用户已翻译的和未翻译的文本
        templateData.put("translated_content", translatedModules);
        templateData.put("remaining_content", unTranslatedModules);

        return emailIntegration.sendEmailByTencent(137317L, MailChimpConstants.TRANSLATION_FAILED_SUBJECT,
                templateData, MailChimpConstants.TENCENT_FROM_EMAIL, usersDO.getEmail());
    }

    public void setCommonTemplate(Map<String, String> templateData, String shopName, String target, Integer translateTime, Integer usedTokenByTask) {
        templateData.put("language", target);
        templateData.put("shop_name", shopName.substring(0, shopName.length() - ".myshopify.com".length()));
        templateData.put("time", translateTime + " minutes");

        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.US);
        templateData.put("credit_count", formatter.format(usedTokenByTask));
    }

    /**
     * 发送订阅计划付费后的邮件
     */
    public void sendSubscribeEmail(String shopName, Integer numberOfCredits) {
        UsersDO usersDO = usersService.getUserByName(shopName);
        Map<String, String> templateData = new HashMap<>();
        templateData.put("user", usersDO.getFirstName());

        // 定义要移除的后缀
        String suffix = ".myshopify.com";
        String targetShop;
        targetShop = shopName.substring(0, shopName.length() - suffix.length());
        templateData.put("shop_name", targetShop);

        // 获得用户总的token数
        TranslationCounterDO translationCounterDO = translationCounterService.readCharsByShopName(shopName);
        Integer maxCharsByShopName = translationCounterService.getMaxCharsByShopName(shopName);
        Integer totalCreditsCount = maxCharsByShopName - translationCounterDO.getUsedChars();
        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.US);
        templateData.put("number_of_credits", formatter.format(numberOfCredits));
        templateData.put("total_credits_count", formatter.format(totalCreditsCount));

        // 由腾讯发送邮件
        Boolean b = emailIntegration.sendEmailByTencent(new TencentSendEmailRequest(143058L, templateData,
                MailChimpConstants.SUBSCRIBE_SUCCESSFUL_SUBJECT, MailChimpConstants.TENCENT_FROM_EMAIL, usersDO.getEmail()));

        // 存入数据库中
        emailService.saveEmail(new EmailDO(0, shopName, MailChimpConstants.TENCENT_FROM_EMAIL, usersDO.getEmail(),
                MailChimpConstants.SUBSCRIBE_SUCCESSFUL_SUBJECT, b ? 1 : 0));
    }

    /**
     * 发送APG应用初始化邮件
     *
     * @param email  接收人的邮箱
     * @param userId 接收人的商店id
     */
    public void sendApgInitEmail(String email, Long userId) {
        //由腾讯发送邮件
        Boolean b = emailIntegration.sendEmailByTencent(new TencentSendEmailRequest(144208L, null,
                MailChimpConstants.APG_INIT_EMAIL, MailChimpConstants.TENCENT_FROM_EMAIL, email));
        //存入数据库中
        iapgEmailService.saveEmail(new APGEmailDO(null, userId, MailChimpConstants.TENCENT_FROM_EMAIL, email,
                MailChimpConstants.APG_INIT_EMAIL, b));
    }

    /**
     * 发送生成描述成功的邮件 144209
     *
     * @param email  接收人的邮箱
     * @param userId 接收人的商店id
     */
    public void sendAPGSuccessEmail(String email, Long userId, String taskType, String userName, Timestamp createTime, Integer totalToken, Integer products, Integer remaining) {
        //根据用户的id获取生成模块的相关数据
        Map<String, String> templateData = new HashMap<>();
        templateData.put("task_type", taskType); // taskType: product, collection
        templateData.put("username", userName);
        templateData.put("product_count", String.valueOf(products));
        Instant now = Instant.now();
        Instant created = createTime.toInstant();

        long seconds = Duration.between(created, now).getSeconds();
        templateData.put("duration", String.valueOf(seconds));
        //将下面数据改为千分位
        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.US);
        templateData.put("credit_used", formatter.format(totalToken));
        templateData.put("credit_remaining", formatter.format(remaining));
        //设置参数
        Boolean b = emailIntegration.sendEmailByTencent(new TencentSendEmailRequest(144209L, templateData,
                MailChimpConstants.APG_GENERATE_SUCCESS, MailChimpConstants.TENCENT_FROM_EMAIL, email));
        //存入数据库中
        iapgEmailService.saveEmail(new APGEmailDO(null, userId, MailChimpConstants.TENCENT_FROM_EMAIL, email,
                MailChimpConstants.APG_GENERATE_SUCCESS, b));
    }

    /**
     * 发送APG应用购买成功的邮件
     */
    public Integer sendAPGPurchaseEmail(APGUsersDO apgUsersDO, Integer numberOfCredits, Double creditAmount, Integer creditBalance) {
        Map<String, String> templateData = new HashMap<>();
        templateData.put("username", apgUsersDO.getFirstName());
        // 获得用户总的token数
        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.US);
        templateData.put("purchase_amount", formatter.format(numberOfCredits));
        templateData.put("credit_amount", formatter.format(creditAmount));
        templateData.put("credit_balance", formatter.format(creditBalance));
        //由腾讯发送邮件
        Boolean b = emailIntegration.sendEmailByTencent(new TencentSendEmailRequest(144922L, templateData,
                MailChimpConstants.APG_PURCHASE_EMAIL, MailChimpConstants.TENCENT_FROM_EMAIL, apgUsersDO.getEmail()));
        //存入数据库中
        return iapgEmailService.saveEmail(new APGEmailDO(null, apgUsersDO.getId(), MailChimpConstants.TENCENT_FROM_EMAIL,
                apgUsersDO.getEmail(), MailChimpConstants.APG_PURCHASE_EMAIL, b)) ? 1 : 0;
    }

    /**
     * 发送APG应用任务中断的邮件
     */
    public void sendAPGTaskInterruptEmail(APGUsersDO apgUsersDO, Integer completedCount, Integer remainingCount, Integer creditBalance) {
        Map<String, String> templateData = new HashMap<>();
        templateData.put("username", apgUsersDO.getFirstName());
        //存用户翻译任务等数据
        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.US);
        templateData.put("credit_balance", formatter.format(creditBalance));
        templateData.put("completed_count", String.valueOf(completedCount));
        templateData.put("remaining_count", String.valueOf(remainingCount));
        //由腾讯发送邮件
        Boolean b = emailIntegration.sendEmailByTencent(new TencentSendEmailRequest(144923L, templateData,
                MailChimpConstants.APG_TASK_INTERRUPT_EMAIL, MailChimpConstants.TENCENT_FROM_EMAIL, apgUsersDO.getEmail()));
        //存入数据库中
        iapgEmailService.saveEmail(new APGEmailDO(null, apgUsersDO.getId(), MailChimpConstants.TENCENT_FROM_EMAIL,
                apgUsersDO.getEmail(), MailChimpConstants.APG_TASK_INTERRUPT_EMAIL, b));
    }

    /**
     * 发送ip上报邮件 156623L
     */
    public void sendIpReportEmail(String shopName, int noCurrencyCount, int noLanguageCount,
                                  List<Map<String, Integer>> languageEmailData, List<Map<String, Integer>> currencyEmailData) {
        UsersDO user = usersService.getUserByName(shopName);
        if (user == null) {
            AppInsightsUtils.trackTrace("FatalException sendIpReportEmail user is null " + shopName);
            return;
        }

        String targetShop = shopName.replace(".myshopify.com", "");

        Map<String, String> templateData = new HashMap<>();
        templateData.put("name", user.getFirstName());
        templateData.put("admin", targetShop);
        templateData.put("missing_currency_count", String.valueOf(noCurrencyCount));
        templateData.put("missing_language_count", String.valueOf(noLanguageCount));
        templateData.put("estimated_revenue_loss", String.valueOf((noLanguageCount + noCurrencyCount) * 0.5));

        // HTML 列表生成
        templateData.put("Language_top",
                buildHtmlList(languageEmailData, ModuleCodeUtils::getLanguageName));

        templateData.put("Currency_top",
                buildHtmlList(currencyEmailData, CurrencyConfig::getCurrentName));
        // 发送邮件（如果需要）
        boolean result = emailIntegration.sendEmailByTencent(new TencentSendEmailRequest(156623L,
                templateData, MailChimpConstants.IP_REPORT_EMAIL, MailChimpConstants.TENCENT_FROM_EMAIL, user.getEmail()));
        AppInsightsUtils.trackTrace("sendIpReportEmail " + shopName + " 邮件发送结果为： " + result);
    }

    /**
     * 根据传入的数据构建 HTML 列表。
     *
     * @param data         数据列表，每条数据是 Map<code, count>
     * @param nameResolver 将 code 转成可读名称
     */
    private String buildHtmlList(List<Map<String, Integer>> data,
                                 Function<String, String> nameResolver) {

        if (data == null || data.isEmpty()) {
            return "";
        }

        return data.stream()
                .flatMap(map -> map.entrySet().stream())
                .filter(entry -> {
                    String displayName = nameResolver.apply(entry.getKey());
                    return !entry.getKey().equals(displayName);
                })
                .map(entry -> {
                    String displayName = nameResolver.apply(entry.getKey());
                    int value = entry.getValue();
                    return String.format(
                            "<li>%s - %d visitors</li>\n",
                            displayName,
                            value
                    );
                })
                .collect(Collectors.joining());
    }

    public Boolean sendInitialUserEmail(long emailId, Map<String, String> templateData, String firstInstallSubject, String tencentFromEmail, String email) {
        return emailIntegration.sendEmailByTencent(new TencentSendEmailRequest(emailId,
                templateData, firstInstallSubject, tencentFromEmail, email));
    }

    public void sendThemeEmail(String shopName) {
        UsersDO usersDO = usersService.getUserByName(shopName);
        Map<String, String> templateData = new HashMap<>();
        templateData.put("name", usersDO.getFirstName());

        // 定义要移除的后缀
        String name = parseShopName(shopName);
        templateData.put("admin", name);
        Boolean flag = emailIntegration.sendEmailByTencent(new TencentSendEmailRequest(159294L,
                templateData, MailChimpConstants.USER_THEME_EMAIL, MailChimpConstants.TENCENT_FROM_EMAIL, usersDO.getEmail()));
        emailService.saveEmail(new EmailDO(0, shopName, MailChimpConstants.TENCENT_FROM_EMAIL, usersDO.getEmail(),
                MailChimpConstants.USER_THEME_EMAIL, flag ? 1 : 0));
    }

    public void sendDefaultLanguageEmail(String shopName) {
        UsersDO usersDO = usersService.getUserByName(shopName);
        Map<String, String> templateData = new HashMap<>();
        templateData.put("username", usersDO.getFirstName());

        // 定义要移除的后缀
        String name = parseShopName(shopName);
        templateData.put("admin", name);
        Boolean flag = emailIntegration.sendEmailByTencent(new TencentSendEmailRequest(159295L,
                templateData, MailChimpConstants.USER_LANGUAGE_EMAIL, MailChimpConstants.TENCENT_FROM_EMAIL, usersDO.getEmail()));
        emailService.saveEmail(new EmailDO(0, shopName, MailChimpConstants.TENCENT_FROM_EMAIL, usersDO.getEmail(),
                MailChimpConstants.USER_LANGUAGE_EMAIL, flag ? 1 : 0));
    }

    // 这个switch的邮件是要的，不是无用代码
    public void sendThemeSwitchEmail(String shopName) {
        UsersDO usersDO = usersService.getUserByName(shopName);
        Map<String, String> templateData = new HashMap<>();
        templateData.put("username", usersDO.getFirstName());

        // 定义要移除的后缀
        String name = parseShopName(shopName);
        templateData.put("admin", name);
        Boolean flag = emailIntegration.sendEmailByTencent(new TencentSendEmailRequest(159296L,
                templateData, MailChimpConstants.USER_SWITCH_EMAIL, MailChimpConstants.TENCENT_FROM_EMAIL, "feynman@ciwi.ai"));
        emailService.saveEmail(new EmailDO(0, shopName, MailChimpConstants.TENCENT_FROM_EMAIL, usersDO.getEmail(),
                MailChimpConstants.USER_SWITCH_EMAIL, flag ? 1 : 0));
    }

    public boolean sendAutoTranslatePartialEmail(String shopName, List<InitialTaskV2DO> partialTasks) {
        String name = parseShopName(shopName);

        UsersDO usersDO = usersService.getUserByName(shopName);
        Map<String, String> templateData = new HashMap<>();
        templateData.put("username", usersDO.getFirstName());
        templateData.put("translation", "auto translation");
        templateData.put("admin", name);

        StringBuilder divBuilder = new StringBuilder();

        for (InitialTaskV2DO taskV2DO : partialTasks) {
            // 计算百分比数据
            Map<String, String> taskMap = translateTaskMonitorV2RedisService.getAllByTaskId(taskV2DO.getId());
            String totalCount = taskMap.getOrDefault("totalCount", null);
            String translatedCount = taskMap.getOrDefault("translatedCount", null);
            if (totalCount == null ||totalCount.isEmpty() || translatedCount == null || translatedCount.isEmpty()) {
                continue;
            }

            int total = Integer.parseInt(totalCount);
            int translated = Integer.parseInt(translatedCount);

            DecimalFormat df = new DecimalFormat("0.00");
            double percentage = translated * 100.0 / total;
            String percentageStr = df.format(percentage);

            divBuilder.append("<tr>")
                    .append("<td style=\"padding:8px;border-bottom:1px solid #e5e7eb;\">")
                    .append(taskV2DO.getTarget())
                    .append("</td>")
                    .append("<td style=\"padding:8px;border-bottom:1px solid #e5e7eb;text-align:right;\">")
                    .append(percentageStr).append("%")
                    .append("</td>")
                    .append("</tr>");
        }

        // 都continue了
        if (divBuilder.toString().isEmpty()) {
            AppInsightsUtils.trackTrace("sendAutoTranslateEmail divBuilder is empty " + shopName);
            return true;
        }
        templateData.put("language_progress_rows", String.valueOf(divBuilder));
        return emailIntegration.sendEmailByTencent(159297L, MailChimpConstants.AUTO_FAILED_EMAIL, templateData,
                MailChimpConstants.TENCENT_FROM_EMAIL, MailChimpConstants.CC_EMAIL);
    }
}
