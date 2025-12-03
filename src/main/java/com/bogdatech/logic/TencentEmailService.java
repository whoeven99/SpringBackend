package com.bogdatech.logic;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bogdatech.Service.*;
import com.bogdatech.config.CurrencyConfig;
import com.bogdatech.constants.MailChimpConstants;
import com.bogdatech.entity.DO.*;
import com.bogdatech.integration.EmailIntegration;
import com.bogdatech.logic.redis.TranslationCounterRedisService;
import com.bogdatech.mapper.InitialTranslateTasksMapper;
import com.bogdatech.model.controller.request.TencentSendEmailRequest;
import com.bogdatech.model.controller.request.TranslateRequest;
import com.bogdatech.model.controller.response.TypeSplitResponse;
import com.bogdatech.utils.ApiCodeUtils;
import com.bogdatech.utils.ResourceTypeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.bogdatech.constants.MailChimpConstants.TENCENT_FROM_EMAIL;
import static com.bogdatech.constants.MailChimpConstants.APG_PURCHASE_EMAIL;
import static com.bogdatech.constants.MailChimpConstants.APG_TASK_INTERRUPT_EMAIL;
import static com.bogdatech.constants.MailChimpConstants.ONLINE_NOT_TRANSLATION_SUBJECT;
import static com.bogdatech.constants.MailChimpConstants.EMAIL_IP_RUNNING_OUT;
import static com.bogdatech.constants.MailChimpConstants.EMAIL_IP_OUT;
import static com.bogdatech.constants.MailChimpConstants.SUCCESSFUL_AUTO_TRANSLATION_SUBJECT;
import static com.bogdatech.constants.MailChimpConstants.SUCCESSFUL_TRANSLATION_SUBJECT;
import static com.bogdatech.constants.MailChimpConstants.SUBSCRIBE_SUCCESSFUL_SUBJECT;
import static com.bogdatech.constants.MailChimpConstants.TRANSLATION_FAILED_SUBJECT;
import static com.bogdatech.constants.MailChimpConstants.APG_INIT_EMAIL;
import static com.bogdatech.constants.MailChimpConstants.APG_GENERATE_SUCCESS;
import static com.bogdatech.constants.TranslateConstants.SHOP_NAME;
import static com.bogdatech.logic.RabbitMqTranslateService.AUTO;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.StringUtils.parseShopName;

@Component
public class TencentEmailService {
    @Autowired
    private EmailIntegration emailIntegration;
    @Autowired
    private IEmailService emailService;
    @Autowired
    private IUsersService usersService;
    @Autowired
    private ITranslationUsageService translationUsageService;
    @Autowired
    private ITranslatesService translatesService;
    @Autowired
    private ITranslationCounterService translationCounterService;
    @Autowired
    private IAPGEmailService iapgEmailService;
    @Autowired
    private InitialTranslateTasksMapper initialTranslateTasksMapper;
    @Autowired
    private TranslationCounterRedisService translationCounterRedisService;

    // 由腾讯发送邮件
    public void sendEmailByEmail(TencentSendEmailRequest tencentSendEmailRequest) {
        emailIntegration.sendEmailByTencent(tencentSendEmailRequest);
    }

    /**
     * 发生未成功翻译的邮件
     */
    public Boolean sendEmailByOnline(String shopName, String source, String target) {
        Map<String, String> templateData = new HashMap<>();
        templateData.put(SHOP_NAME, shopName);
        templateData.put("source_language", source);
        templateData.put("target_language", target);
        return emailIntegration.sendEmailByTencent(
                new TencentSendEmailRequest(134741L, templateData, ONLINE_NOT_TRANSLATION_SUBJECT, TENCENT_FROM_EMAIL, "notification@ciwi.ai"));
    }

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
                new TencentSendEmailRequest(141470L, templateData, EMAIL_IP_RUNNING_OUT, TENCENT_FROM_EMAIL, userByName.getEmail()));
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
                new TencentSendEmailRequest(141471L, templateData, EMAIL_IP_OUT, TENCENT_FROM_EMAIL, userByName.getEmail()));
    }

    /**
     * 发送自动翻译后邮件
     */
    public Boolean sendAutoTranslateEmail(String shopName) {
        String name = parseShopName(shopName);
        UsersDO usersDO = usersService.getUserByName(shopName);
        Map<String, String> templateData = new HashMap<>();
        templateData.put("user", usersDO.getFirstName());
        templateData.put("shop_name", name);
        List<TranslationUsageDO> translationUsageDOS = translationUsageService.readTranslationUsageData(shopName);
        StringBuilder divBuilder = new StringBuilder();
        for (TranslationUsageDO translationUsageDO : translationUsageDOS
        ) {
            if (translationUsageDO.getCreditCount() == 0) {
                continue;
            }
            Integer remainingCredits = translationUsageDO.getRemainingCredits();
            if (remainingCredits < 0) {
                remainingCredits = 0;
            }

            divBuilder.append("<div class=\"language-block\">");
            divBuilder.append("<h4>").append(translationUsageDO.getLanguageName()).append("</h4>");
            divBuilder.append("<ul>");
            divBuilder.append("<li><span>Credits Used:</span> ").append(translationUsageDO.getCreditCount()).append(" credits used").append("</li>");
            divBuilder.append("<li><span>Translation Time:</span> ").append(translationUsageDO.getConsumedTime()).append(" minutes").append("</li>");
            divBuilder.append("<li><span>Credits Remaining:</span> ").append(remainingCredits).append(" credits left").append("</li>");
            divBuilder.append("</ul>");
            divBuilder.append("</div>");
        }
        templateData.put("html_data", String.valueOf(divBuilder));
//        appInsights.trackTrace("templateData" + templateData);
        //由腾讯发送邮件
        //判断邮件代码divBuilder里面是否为空，空就不发送邮件
        if (divBuilder.toString().isEmpty()) {
//            appInsights.trackTrace("divBuilder is empty");
            return true;
        }
        Boolean b = emailIntegration.sendEmailByTencent(new TencentSendEmailRequest(140352L, templateData, SUCCESSFUL_AUTO_TRANSLATION_SUBJECT, TENCENT_FROM_EMAIL, usersDO.getEmail()));
        //存入数据库中
        emailService.saveEmail(new EmailDO(0, shopName, TENCENT_FROM_EMAIL, usersDO.getEmail(), SUCCESSFUL_TRANSLATION_SUBJECT, b ? 1 : 0));
        return b;
    }

    //翻译失败后发送邮件
    public void translateFailEmail(String shopName, LocalDateTime begin, List<TranslateResourceDTO> resourceList, String target, String source, Long costChars) {
        UsersDO usersDO = usersService.getUserByName(shopName);
        Map<String, String> templateData = new HashMap<>();
        templateData.put("language", target);
        templateData.put("user", usersDO.getFirstName());

        // 定义要移除的后缀
        String suffix = ".myshopify.com";
        String targetShop;
        targetShop = shopName.substring(0, shopName.length() - suffix.length());
        templateData.put("shop_name", targetShop);

        // 获取用户已翻译的和未翻译的文本
        // 通过shopName获取翻译到那个文本
        String resourceType = translatesService.getResourceTypeByshopNameAndTargetAndSource(shopName, target, source);
        TypeSplitResponse typeSplitResponse = ResourceTypeUtils.splitByType(resourceType, resourceList);
        templateData.put("translated_content", typeSplitResponse.getBefore().toString());
        templateData.put("remaining_content", typeSplitResponse.getAfter().toString());

        // 获取更新前后的时间
        LocalDateTime end = LocalDateTime.now();

        Duration duration = Duration.between(begin, end);
        long costTime = duration.toMinutes();
        templateData.put("time", costTime + " minutes");

        // 共消耗的字符数
        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.US);

        int costCharsInt = costChars.intValue();
        if (costCharsInt <= 0) {
            costCharsInt = 0;
        }
        String formattedNumber = formatter.format(costCharsInt);
        templateData.put("credit_count", formattedNumber);

        // 由腾讯发送邮件
        Boolean b = emailIntegration.sendEmailByTencent(new TencentSendEmailRequest(137317L, templateData, TRANSLATION_FAILED_SUBJECT, TENCENT_FROM_EMAIL, usersDO.getEmail()));

        // 存入数据库中
        emailService.saveEmail(new EmailDO(0, shopName, TENCENT_FROM_EMAIL, usersDO.getEmail(), TRANSLATION_FAILED_SUBJECT, b ? 1 : 0));
    }

    //翻译成功后发送邮件
    public void translateSuccessEmail(TranslateRequest request, LocalDateTime begin, Long costToken, Integer usedChars, Integer remainingChars) {
        String shopName = request.getShopName();

        // 通过shopName获取用户信息 需要 {{user}} {{language}} {{credit_count}} {{time}} {{remaining_credits}}
        UsersDO usersDO = usersService.getUserByName(shopName);
        Map<String, String> templateData = new HashMap<>();
        templateData.put("user", usersDO.getFirstName());
        templateData.put("language", request.getTarget());

        // 定义要移除的后缀
        String suffix = ".myshopify.com";
        String targetShop;
        targetShop = request.getShopName().substring(0, request.getShopName().length() - suffix.length());
        templateData.put("shop_name", targetShop);

        // 获取更新前后的时间
        LocalDateTime end = LocalDateTime.now();
        Duration duration = Duration.between(begin, end);
        long costTime = duration.toMinutes();
        if (costTime < 0) {
            costTime = 0;
        }
        templateData.put("time", costTime + " minutes");

        // 共消耗的字符数
        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.US);

        int costChars = costToken.intValue();
        String formattedNumber = formatter.format(costChars);
        templateData.put("credit_count", formattedNumber);

        // 还剩下的字符数
        int endChars = remainingChars - usedChars;
        if (endChars < 0) {
            templateData.put("remaining_credits", "0");

        } else {
            String formattedNumber2 = formatter.format(endChars);
            templateData.put("remaining_credits", formattedNumber2);
        }
        appInsights.trackTrace(shopName + "  templateData ： " + templateData);

        // 由腾讯发送邮件
        Boolean flag = emailIntegration.sendEmailByTencent(new TencentSendEmailRequest(137353L, templateData, SUCCESSFUL_TRANSLATION_SUBJECT, TENCENT_FROM_EMAIL, usersDO.getEmail()));

        // 存入数据库中
        emailService.saveEmail(new EmailDO(0, shopName, TENCENT_FROM_EMAIL, usersDO.getEmail(), SUCCESSFUL_TRANSLATION_SUBJECT, flag ? 1 : 0));
    }

    /**
     * 自动翻译发送逻辑
     */
    public void autoTranslateSendEmail(TranslateRequest request, int costChars, long costTime, int remaining) {
        try {
            String shopName = request.getShopName();
            //将翻译成功的数据，存到数据库中
            List<TranslatesDO> list = translatesService.list(new QueryWrapper<TranslatesDO>().eq("shop_name", shopName).eq("auto_translate", true));
            //将list里面的数据，存到TranslationUsage表里面
            translationUsageService.insertListData(list, shopName);
            Integer targetId = list.stream()
                    .filter(item -> request.getTarget().equals(item.getTarget()) && Boolean.TRUE.equals(item.getAutoTranslate()))
                    .map(TranslatesDO::getId)
                    .findFirst()
                    .orElse(null);
            translationUsageService.insertOrUpdateSingleData(new TranslationUsageDO(targetId, shopName, request.getTarget(), costChars, (int) costTime, remaining, 1));
        } catch (Exception e) {
            appInsights.trackTrace("自动翻译存储数据失败 errors ：" + e.getMessage());
        }
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
        //共消耗的字符数
//        appInsights.trackTrace("templateData" + templateData);
        //由腾讯发送邮件
        Boolean b = emailIntegration.sendEmailByTencent(new TencentSendEmailRequest(143058L, templateData, SUBSCRIBE_SUCCESSFUL_SUBJECT, TENCENT_FROM_EMAIL, usersDO.getEmail()));
        //存入数据库中
        emailService.saveEmail(new EmailDO(0, shopName, TENCENT_FROM_EMAIL, usersDO.getEmail(), SUBSCRIBE_SUCCESSFUL_SUBJECT, b ? 1 : 0));
    }

    /**
     * 发送APG应用初始化邮件
     *
     * @param email  接收人的邮箱
     * @param userId 接收人的商店id
     */
    public void sendApgInitEmail(String email, Long userId) {
        //由腾讯发送邮件
        Boolean b = emailIntegration.sendEmailByTencent(new TencentSendEmailRequest(144208L, null, APG_INIT_EMAIL, TENCENT_FROM_EMAIL, email));
        //存入数据库中
        iapgEmailService.saveEmail(new APGEmailDO(null, userId, TENCENT_FROM_EMAIL, email, APG_INIT_EMAIL, b));
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
        Boolean b = emailIntegration.sendEmailByTencent(new TencentSendEmailRequest(144209L, templateData, APG_GENERATE_SUCCESS, TENCENT_FROM_EMAIL, email));
        //存入数据库中
        iapgEmailService.saveEmail(new APGEmailDO(null, userId, TENCENT_FROM_EMAIL, email, APG_GENERATE_SUCCESS, b));
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
        Boolean b = emailIntegration.sendEmailByTencent(new TencentSendEmailRequest(144922L, templateData, APG_PURCHASE_EMAIL, TENCENT_FROM_EMAIL, apgUsersDO.getEmail()));
        //存入数据库中
        return iapgEmailService.saveEmail(new APGEmailDO(null, apgUsersDO.getId(), TENCENT_FROM_EMAIL, apgUsersDO.getEmail(), APG_PURCHASE_EMAIL, b)) ? 1 : 0;
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
        Boolean b = emailIntegration.sendEmailByTencent(new TencentSendEmailRequest(144923L, templateData, APG_TASK_INTERRUPT_EMAIL, TENCENT_FROM_EMAIL, apgUsersDO.getEmail()));
        //存入数据库中
        iapgEmailService.saveEmail(new APGEmailDO(null, apgUsersDO.getId(), TENCENT_FROM_EMAIL, apgUsersDO.getEmail(), APG_TASK_INTERRUPT_EMAIL, b));
    }

    /**
     * EMAIL_AUTO的邮件发送
     */
    public void emailAutoTranslate(String shopName, String target, LocalDateTime begin, int usedChars, int limitChars, String taskId) {
        try {
            // 获取更新前后的时间
            LocalDateTime end = LocalDateTime.now();
            Duration duration = Duration.between(begin, end);
            long costTime = duration.toMinutes();
            int endChars = limitChars - usedChars;
            // 判断数据库里面是否存在该语言
            translationUsageService.update(new LambdaUpdateWrapper<TranslationUsageDO>()
                    .eq(TranslationUsageDO::getShopName, shopName)
                    .eq(TranslationUsageDO::getLanguageName, target)
                    .set(TranslationUsageDO::getConsumedTime, costTime)
                    .set(TranslationUsageDO::getCreditCount, usedChars)
                    .set(TranslationUsageDO::getRemainingCredits, endChars)
                    .set(TranslationUsageDO::getStatus, 1));

            // 判断TranslationUsage里面的语言是否都翻译了，如果有就发送邮件；没有的话，就跳过
            List<TranslatesDO> list = translatesService.list(new QueryWrapper<TranslatesDO>().eq("shop_name", shopName).eq("auto_translate", true));
            appInsights.trackTrace("emailAutoTranslate 用户： " + shopName + " 自动翻译列表大小为： " + list.size() + " 自动翻译列表： " + list);
            Boolean b = translationUsageService.judgeSendAutoEmail(list, shopName);
            if (b) {
                appInsights.trackTrace("emailAutoTranslate 用户 " + shopName + " 条件符合发送邮件 时间为： " + LocalDateTime.now());
                sendAutoTranslateEmail(shopName);
                //将所有status, remaining，consumed， credit都改为0
                translationUsageService.update(new LambdaUpdateWrapper<TranslationUsageDO>()
                        .eq(TranslationUsageDO::getShopName, shopName)
                        .set(TranslationUsageDO::getStatus, 0)
                        .set(TranslationUsageDO::getRemainingCredits, 0)
                        .set(TranslationUsageDO::getConsumedTime, 0)
                        .set(TranslationUsageDO::getCreditCount, 0));

                // 更新状态
                List<String> targetList = list.stream().map(TranslatesDO::getTarget).toList();
                if (targetList.isEmpty()) {
                    appInsights.trackTrace("emailAutoTranslate 用户 " + shopName + " targetList为空 " + list);
                    return;
                }

                initialTranslateTasksMapper.update(new LambdaUpdateWrapper<InitialTranslateTasksDO>()
                        .eq(InitialTranslateTasksDO::getShopName, shopName)
                        .eq(InitialTranslateTasksDO::getStatus, 1)
                        .eq(InitialTranslateTasksDO::getTaskType, AUTO)
                        .in(InitialTranslateTasksDO::getTarget, targetList)
                        .set(InitialTranslateTasksDO::getStatus, 4)
                        .set(InitialTranslateTasksDO::isSendEmail, true)
                        .set(InitialTranslateTasksDO::isDeleted, true));
            }

            appInsights.trackTrace("emailAutoTranslate 用户 " + shopName + " 自动翻译结束 时间为： " + LocalDateTime.now());
        } catch (Exception e) {
            appInsights.trackTrace("emailAutoTranslate " + shopName + " 邮件发送 errors : " + e);
            appInsights.trackException(e);
        }
    }

    /**
     * 发送ip上报邮件 156623L
     */
    public void sendIpReportEmail(String shopName, int noCurrencyCount, int noLanguageCount,
                                  List<Map<String, Integer>> languageEmailData, List<Map<String, Integer>> currencyEmailData) {
        UsersDO user = usersService.getUserByName(shopName);
        if (user == null) {
            appInsights.trackTrace("sendIpReportEmail user is null " + shopName);
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
                buildHtmlList(languageEmailData, ApiCodeUtils::getLanguageName));

        templateData.put("Currency_top",
                buildHtmlList(currencyEmailData, CurrencyConfig::getCurrentName));

        // 发送邮件（如果需要）
        boolean result = emailIntegration.sendEmailByTencent(new TencentSendEmailRequest(156623L,
                        templateData, MailChimpConstants.IP_REPORT_EMAIL, TENCENT_FROM_EMAIL, user.getEmail()));
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
                .map(entry -> {
                    String displayName = nameResolver.apply(entry.getKey());
                    int value = entry.getValue();
                    return String.format("<li>%s - %d visitors</li>\n", displayName, value);
                })
                .collect(Collectors.joining());
    }
}
