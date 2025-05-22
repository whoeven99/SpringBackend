package com.bogdatech.logic;

import com.bogdatech.Service.IEmailService;
import com.bogdatech.Service.ITranslationUsageService;
import com.bogdatech.Service.IUsersService;
import com.bogdatech.entity.DO.EmailDO;
import com.bogdatech.entity.DO.TranslationUsageDO;
import com.bogdatech.entity.DO.UsersDO;
import com.bogdatech.integration.EmailIntegration;
import com.bogdatech.model.controller.request.TencentSendEmailRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.bogdatech.constants.MailChimpConstants.*;
import static com.bogdatech.constants.TranslateConstants.SHOP_NAME;
import static com.bogdatech.utils.StringUtils.parseShopName;

@Component
public class TencentEmailService {

    private final EmailIntegration emailIntegration;
    private final IEmailService emailService;
    private final IUsersService usersService;
    private final ITranslationUsageService translationUsageService;
    @Autowired
    public TencentEmailService(EmailIntegration emailIntegration, IEmailService emailService, IUsersService usersService, ITranslationUsageService translationUsageService) {
        this.emailIntegration = emailIntegration;
        this.emailService = emailService;
        this.usersService = usersService;
        this.translationUsageService = translationUsageService;
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
        Boolean b = emailIntegration.sendEmailByTencent(new TencentSendEmailRequest(140352L, templateData, SUCCESSFUL_AUTO_TRANSLATION_SUBJECT, TENCENT_FROM_EMAIL, "1287127238@qq.com"));

        //存入数据库中
        emailService.saveEmail(new EmailDO(0, shopName, TENCENT_FROM_EMAIL, "1287127238@qq.com", SUCCESSFUL_TRANSLATION_SUBJECT, b ? 1 : 0));
        return b;
    }
}
