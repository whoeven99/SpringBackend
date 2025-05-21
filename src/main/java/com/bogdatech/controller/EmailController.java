package com.bogdatech.controller;

import com.bogdatech.Service.IEmailService;
import com.bogdatech.entity.EmailDO;
import com.bogdatech.logic.MailChimpService;
import com.bogdatech.logic.TencentEmailService;
import com.bogdatech.model.controller.request.MailChampSendEmailRequest;
import com.bogdatech.model.controller.request.TencentSendEmailRequest;
import com.bogdatech.model.controller.response.BaseResponse;
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
    private final IEmailService emailService;
    @Autowired
    public EmailController(MailChimpService mailChimpService, TencentEmailService tencentEmailService, IEmailService emailService) {
        this.mailChimpService = mailChimpService;
        this.tencentEmailService = tencentEmailService;
        this.emailService = emailService;
    }

    @PostMapping("/send")
    public String sendEmail(String toEmail) {
//        return emailIntegration.sendEmail(new SendEmailRequest(null, CHARACTER_PURCHASE_SUCCESSFUL, "EMAIL", "daoyee@ciwi.ai",
//                "Welcome to Ciwi.ai! Unlock a New Language Translation Experience", "support@ciwi.ai", "daoyee@ciwi.ai"));
        return mailChimpService.sendTranslateFailMail(new MailChampSendEmailRequest(null, null,
                null,null, null, toEmail, "daoye"), "ture");
//        return null;
    }

    //由腾讯发送邮件
    @PostMapping("/sendByTencent")
    public void sendEmailByTencent(@RequestBody TencentSendEmailRequest TencentSendEmailRequest) {
        tencentEmailService.sendEmailByEmail(TencentSendEmailRequest);

    }

    //将翻译的数据存储到数据库
    @PostMapping("/saveEmail")
    public BaseResponse<Object> saveTranslate(@RequestBody EmailDO emailDO) {
        Integer i = emailService.saveEmail(emailDO);
        if (i > 0 ){
            return new BaseResponse<>().CreateSuccessResponse(true);
        }else {
            return new BaseResponse<>().CreateErrorResponse("false");
        }
    }

    @PostMapping("/sendOnlineEmail")
    public BaseResponse<Object> sendEmail(String shopName, String target, String source) {
        Boolean b = tencentEmailService.sendEmailByOnline(shopName, target, source);
        if(b){
            return new BaseResponse<>().CreateSuccessResponse(true);
        }
        else {
            return new BaseResponse<>().CreateErrorResponse("false");
        }
    }

    @PostMapping("/sendAutoTranslateEmail")
    public BaseResponse<Object> sendAutoTranslateEmail() {

        return null;
    }
}
