package com.bogdatech.controller;

import com.bogdatech.logic.MailChimpService;
import com.bogdatech.model.controller.request.SendEmailRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/email")
public class EmailController {
    private final MailChimpService mailChimpService;
    @Autowired
    public EmailController(MailChimpService mailChimpService) {
        this.mailChimpService = mailChimpService;
    }

    @PostMapping("/send")
    public String sendEmail() {
//        return emailIntegration.sendEmail(new SendEmailRequest(null, CHARACTER_PURCHASE_SUCCESSFUL, "EMAIL", "daoyee@ciwi.ai",
//                "Welcome to Ciwi.ai! Unlock a New Language Translation Experience", "support@ciwi.ai", "daoyee@ciwi.ai"));
        return mailChimpService.sendFirstInstallMail(new SendEmailRequest(null, null,
                null,null, null, "daoyee@ciwi.ai", "daoye"));
//        return null;
    }
}
