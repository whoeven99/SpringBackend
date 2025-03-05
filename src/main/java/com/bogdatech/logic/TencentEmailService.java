package com.bogdatech.logic;

import com.bogdatech.integration.EmailIntegration;
import com.bogdatech.model.controller.request.TencentSendEmailRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

import static com.bogdatech.constants.MailChimpConstants.*;
import static com.bogdatech.constants.TranslateConstants.SHOP_NAME;

@Component
public class TencentEmailService {

    private final EmailIntegration emailIntegration;
    @Autowired
    public TencentEmailService(EmailIntegration emailIntegration) {
        this.emailIntegration = emailIntegration;
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

}
