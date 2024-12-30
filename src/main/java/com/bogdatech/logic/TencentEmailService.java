package com.bogdatech.logic;

import com.bogdatech.integration.EmailIntegration;
import com.bogdatech.model.controller.request.TencentSendEmailRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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
}
