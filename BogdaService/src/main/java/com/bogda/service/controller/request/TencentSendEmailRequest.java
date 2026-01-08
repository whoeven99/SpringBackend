package com.bogda.service.controller.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TencentSendEmailRequest {
    private Long templateId;
    private Map<String, String> templateData;
    private String subject;
    private String fromEmail;
    private String toEmail;
}
