package com.bogdatech.model.controller.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SendEmailRequest {
    private String emailKey;
    private String templateName;
    private String templateContent;
    private String subject;
    private String fromEmail;
    private String toEmail;
    private String user;
}
