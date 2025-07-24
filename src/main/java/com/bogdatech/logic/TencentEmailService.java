package com.bogdatech.logic;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bogdatech.Service.*;
import com.bogdatech.entity.DO.*;
import com.bogdatech.integration.EmailIntegration;
import com.bogdatech.model.controller.request.TencentSendEmailRequest;
import com.bogdatech.model.controller.request.TranslateRequest;
import com.bogdatech.model.controller.response.TypeSplitResponse;
import com.bogdatech.utils.CharacterCountUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.bogdatech.constants.MailChimpConstants.*;
import static com.bogdatech.constants.TranslateConstants.SHOP_NAME;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.ResourceTypeUtils.splitByType;
import static com.bogdatech.utils.StringUtils.parseShopName;

@Component
public class TencentEmailService {

    private final EmailIntegration emailIntegration;
    private final IEmailService emailService;
    private final IUsersService usersService;
    private final ITranslationUsageService translationUsageService;
    private final ITranslatesService translatesService;
    private final ITranslationCounterService translationCounterService;
    private final IAPGEmailService iapgEmailService;
    @Autowired
    public TencentEmailService(EmailIntegration emailIntegration, IEmailService emailService, IUsersService usersService, ITranslationUsageService translationUsageService, ITranslatesService translatesService, ITranslationCounterService translationCounterService, IAPGEmailService iapgEmailService) {
        this.emailIntegration = emailIntegration;
        this.emailService = emailService;
        this.usersService = usersService;
        this.translationUsageService = translationUsageService;
        this.translatesService = translatesService;
        this.translationCounterService = translationCounterService;
        this.iapgEmailService = iapgEmailService;
    }

    //由腾讯发送邮件
    public void sendEmailByEmail(TencentSendEmailRequest tencentSendEmailRequest) {
        emailIntegration.sendEmailByTencent(tencentSendEmailRequest);
    }

    //发生主题未翻译的邮件
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
     * */
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
     * */
    public Boolean sendEmailByIpOut(String shopName) {
        UsersDO userByName = usersService.getUserByName(shopName);
        String name = parseShopName(shopName);
        Map<String, String> templateData = new HashMap<>();
        templateData.put("user", userByName.getFirstName());
        templateData.put("shop_name", name);
        return emailIntegration.sendEmailByTencent(
                new TencentSendEmailRequest(141471L, templateData, EMAIL_IP_OUT, TENCENT_FROM_EMAIL, userByName.getEmail()));
    }

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
            if (translationUsageDO.getCreditCount() == 0){
                continue;
            }
            divBuilder.append("<div class=\"language-block\">");
            divBuilder.append("<h4>").append(translationUsageDO.getLanguageName()).append("</h4>");
            divBuilder.append("<ul>");
            divBuilder.append("<li><span>Credits Used:</span> ").append(translationUsageDO.getCreditCount()).append(" credits used").append("</li>");
            divBuilder.append("<li><span>Translation Time:</span> ").append(translationUsageDO.getConsumedTime()).append(" minutes").append("</li>");
            divBuilder.append("<li><span>Credits Remaining:</span> ").append(translationUsageDO.getRemainingCredits()).append(" credits left").append("</li>");
            divBuilder.append("</ul>");
            divBuilder.append("</div>");
        }
        templateData.put("html_data", String.valueOf(divBuilder));
//        System.out.println("templateData" + templateData);
        //由腾讯发送邮件
        //判断邮件代码divBuilder里面是否为空，空就不发送邮件
        if (divBuilder.toString().isEmpty()){
//            System.out.println("divBuilder is empty");
            return true;
        }
        Boolean b = emailIntegration.sendEmailByTencent(new TencentSendEmailRequest(140352L, templateData, SUCCESSFUL_AUTO_TRANSLATION_SUBJECT, TENCENT_FROM_EMAIL, usersDO.getEmail()));

        //存入数据库中
        emailService.saveEmail(new EmailDO(0, shopName, TENCENT_FROM_EMAIL, usersDO.getEmail(), SUCCESSFUL_TRANSLATION_SUBJECT, b ? 1 : 0));
        return b;
    }

    //翻译失败后发送邮件
    public void translateFailEmail(String shopName, CharacterCountUtils counter, LocalDateTime begin, int beginChars, List<TranslateResourceDTO> resourceList, String target, String source) {
        UsersDO usersDO = usersService.getUserByName(shopName);
        Map<String, String> templateData = new HashMap<>();
        templateData.put("language", target);
        templateData.put("user", usersDO.getFirstName());
        // 定义要移除的后缀
        String suffix = ".myshopify.com";
        String targetShop;
        targetShop = shopName.substring(0, shopName.length() - suffix.length());
        templateData.put("shop_name", targetShop);
        //获取用户已翻译的和未翻译的文本
        //通过shopName获取翻译到那个文本
        String resourceType = translatesService.getResourceTypeByshopNameAndTargetAndSource(shopName, target, source);
        TypeSplitResponse typeSplitResponse = splitByType(resourceType, resourceList);
        templateData.put("translated_content", typeSplitResponse.getBefore().toString());
        templateData.put("remaining_content", typeSplitResponse.getAfter().toString());
        //获取更新前后的时间
        LocalDateTime end = LocalDateTime.now();

        Duration duration = Duration.between(begin, end);
        long costTime = duration.toMinutes();
        templateData.put("time", costTime + " minutes");

        //共消耗的字符数
        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.US);
        int endChars = counter.getTotalChars();
        int costChars = endChars - beginChars;
        String formattedNumber = formatter.format(costChars);
        templateData.put("credit_count", formattedNumber);
//        System.out.println("templateData" + templateData);
        //由腾讯发送邮件
        Boolean b = emailIntegration.sendEmailByTencent(new TencentSendEmailRequest(137317L, templateData, TRANSLATION_FAILED_SUBJECT, TENCENT_FROM_EMAIL, usersDO.getEmail()));
        //存入数据库中
        emailService.saveEmail(new EmailDO(0, shopName, TENCENT_FROM_EMAIL, usersDO.getEmail(), TRANSLATION_FAILED_SUBJECT, b ? 1 : 0));
    }

    //翻译成功后发送邮件
    public void translateSuccessEmail(TranslateRequest request, CharacterCountUtils counter, LocalDateTime begin, int beginChars, Integer remainingChars, Boolean isTask) {
        String shopName = request.getShopName();
        //通过shopName获取用户信息 需要 {{user}} {{language}} {{credit_count}} {{time}} {{remaining_credits}}
        UsersDO usersDO = usersService.getUserByName(shopName);
        Map<String, String> templateData = new HashMap<>();
        templateData.put("user", usersDO.getFirstName());
        templateData.put("language", request.getTarget());
        // 定义要移除的后缀
        String suffix = ".myshopify.com";
        String targetShop;
        targetShop = request.getShopName().substring(0, request.getShopName().length() - suffix.length());
        templateData.put("shop_name", targetShop);
        //获取更新前后的时间
        LocalDateTime end = LocalDateTime.now();

        Duration duration = Duration.between(begin, end);
        long costTime = duration.toMinutes();
        templateData.put("time", costTime + " minutes");

        //共消耗的字符数
        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.US);
        int endChars = counter.getTotalChars();
        int costChars = endChars - beginChars;
        String formattedNumber = formatter.format(costChars);
        templateData.put("credit_count", formattedNumber);

        //还剩下的字符数
        int remaining = remainingChars - endChars;
        if (remaining < 0) {
            templateData.put("remaining_credits", "0");

        } else {
            String formattedNumber2 = formatter.format(remaining);
            templateData.put("remaining_credits", formattedNumber2);
        }
        appInsights.trackTrace("templateData" + templateData);
        //由腾讯发送邮件
        Boolean b;
        if (isTask) {
            autoTranslateSendEmail(request, costChars, costTime, remaining);
        } else {
            b = emailIntegration.sendEmailByTencent(new TencentSendEmailRequest(137353L, templateData, SUCCESSFUL_TRANSLATION_SUBJECT, TENCENT_FROM_EMAIL, usersDO.getEmail()));
            //存入数据库中
            emailService.saveEmail(new EmailDO(0, shopName, TENCENT_FROM_EMAIL, usersDO.getEmail(), SUCCESSFUL_TRANSLATION_SUBJECT, b ? 1 : 0));
        }
    }

    //自动翻译发送逻辑
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
            appInsights.trackTrace("自动翻译存储数据失败：" + e.getMessage());
        }
    }

    /**
     * 发送订阅计划付费后的邮件
     * */
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
        templateData.put("number_of_credits",  formatter.format(numberOfCredits));
        templateData.put("total_credits_count",  formatter.format(totalCreditsCount));
        //共消耗的字符数
//        System.out.println("templateData" + templateData);
        //由腾讯发送邮件
        Boolean b = emailIntegration.sendEmailByTencent(new TencentSendEmailRequest(143058L, templateData, SUBSCRIBE_SUCCESSFUL_SUBJECT, TENCENT_FROM_EMAIL, usersDO.getEmail()));
        //存入数据库中
        emailService.saveEmail(new EmailDO(0, shopName, TENCENT_FROM_EMAIL, usersDO.getEmail(), SUBSCRIBE_SUCCESSFUL_SUBJECT, b ? 1 : 0));
    }

    /**
     * 发送APG应用初始化邮件
     */
    public void sendApgInitEmail(String email, Long userId) {
        //由腾讯发送邮件
        Boolean b = emailIntegration.sendEmailByTencent(new TencentSendEmailRequest(144208L, null, APG_INIT_EMAIL, TENCENT_FROM_EMAIL, email));
        //存入数据库中
        iapgEmailService.saveEmail(new APGEmailDO(null, userId, TENCENT_FROM_EMAIL, email, APG_INIT_EMAIL, b));
    }

    /**
     * 发送生成描述成功的邮件
     * */
    public void sendAPGSuccessEmail(String shopName, Integer costChars, Integer remainingChars, Boolean isTask) {

    }
}
