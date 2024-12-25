package com.bogdatech.controller;

import com.bogdatech.integration.EmailIntegration;
import com.bogdatech.logic.MailChimpService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/email")
public class EmailController {

    @Autowired
    private EmailIntegration emailIntegration;
    @Autowired
    private MailChimpService mailChimpService;

    @PostMapping("/send")
    public String sendEmail() {
//        return emailIntegration.sendEmail(new SendEmailRequest(null, CHARACTER_PURCHASE_SUCCESSFUL, "EMAIL", "daoyee@ciwi.ai",
//                "Welcome to Ciwi.ai! Unlock a New Language Translation Experience", "support@ciwi.ai", "daoyee@ciwi.ai"));
//        return mailChimpService.sendFirstInstallMail(new SendEmailRequest(null, null,
//                null,null, null, "daoyee@ciwi.ai"));
        return null;
    }
}
