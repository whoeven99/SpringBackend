package com.bogdatech.controller;

import com.bogdatech.Service.IEmailService;
import com.bogdatech.entity.DO.EmailDO;
import com.bogdatech.logic.TencentEmailService;
import com.bogdatech.model.controller.request.TencentSendEmailRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/email")
public class EmailController {
    @Autowired
    private TencentEmailService tencentEmailService;
    @Autowired
    private IEmailService emailService;

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
}
