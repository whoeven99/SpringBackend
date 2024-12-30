package com.bogdatech.controller;

import com.bogdatech.logic.MailChimpService;
import com.bogdatech.logic.TencentEmailService;
import com.bogdatech.model.controller.request.MailChampSendEmailRequest;
import com.bogdatech.model.controller.request.TencentSendEmailRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/email")
public class EmailController {
    private final MailChimpService mailChimpService;
    private final TencentEmailService tencentEmailService;
    @Autowired
    public EmailController(MailChimpService mailChimpService, TencentEmailService tencentEmailService) {
        this.mailChimpService = mailChimpService;
        this.tencentEmailService = tencentEmailService;
    }

    @PostMapping("/send")
    public String sendEmail(String toEmail) {
//        return emailIntegration.sendEmail(new SendEmailRequest(null, CHARACTER_PURCHASE_SUCCESSFUL, "EMAIL", "daoyee@ciwi.ai",
//                "Welcome to Ciwi.ai! Unlock a New Language Translation Experience", "support@ciwi.ai", "daoyee@ciwi.ai"));
        return mailChimpService.sendTranslateFailMail(new MailChampSendEmailRequest(null, null,
                null,null, null, toEmail, "daoye"), "ture");
//        return null;
    }

    //由腾讯发送
    @PostMapping("/sendByTencent")
    public void sendEmailByTencent(@RequestBody TencentSendEmailRequest TencentSendEmailRequest) {
        tencentEmailService.sendEmailByEmail(TencentSendEmailRequest);
    }
}
