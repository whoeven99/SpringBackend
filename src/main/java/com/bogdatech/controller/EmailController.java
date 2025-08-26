package com.bogdatech.controller;

import com.bogdatech.Service.IEmailService;
import com.bogdatech.entity.DO.EmailDO;
import com.bogdatech.logic.MailChimpService;
import com.bogdatech.logic.TencentEmailService;
import com.bogdatech.model.controller.request.MailChampSendEmailRequest;
import com.bogdatech.model.controller.request.TencentSendEmailRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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

    /**
     * 这是最初版用mailChimp发送邮件的接口
     * 可以考虑删除了
     * */
    @PostMapping("/send")
    public String sendEmail(@RequestParam String toEmail) {
        return mailChimpService.sendTranslateFailMail(new MailChampSendEmailRequest(null, null,
                null,null, null, toEmail, "daoye"), "ture");
    }

    /**
     * 由腾讯发送邮件
     * */
    @PostMapping("/sendByTencent")
    public void sendEmailByTencent(@RequestBody TencentSendEmailRequest TencentSendEmailRequest) {
        tencentEmailService.sendEmailByEmail(TencentSendEmailRequest);

    }

    /**
     * 将翻译的数据存储到数据库
     * */
    @PostMapping("/saveEmail")
    public BaseResponse<Object> saveTranslate(@RequestBody EmailDO emailDO) {
        Integer i = emailService.saveEmail(emailDO);
        if (i > 0 ){
            return new BaseResponse<>().CreateSuccessResponse(true);
        }else {
            return new BaseResponse<>().CreateErrorResponse(false);
        }
    }

    /**
     *发生未成功翻译的邮件
     * */
    @PostMapping("/sendOnlineEmail")
    public BaseResponse<Object> sendEmail(@RequestParam String shopName, @RequestParam String target, @RequestParam String source) {
        Boolean b = tencentEmailService.sendEmailByOnline(shopName, target, source);
        if(b){
            return new BaseResponse<>().CreateSuccessResponse(true);
        }
        else {
            return new BaseResponse<>().CreateErrorResponse(false);
        }
    }

    /**
     * 发送自动翻译的邮件
     * */
    @PostMapping("/sendAutoTranslateEmail")
    public BaseResponse<Object> sendAutoTranslateEmail() {
        Boolean b = tencentEmailService.sendAutoTranslateEmail("shopName");
        if(b){
            return new BaseResponse<>().CreateSuccessResponse(true);
        }
        else {
            return new BaseResponse<>().CreateErrorResponse("false");
        }
    }



}
