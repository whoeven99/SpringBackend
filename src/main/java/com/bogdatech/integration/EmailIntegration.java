package com.bogdatech.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class EmailIntegration {

    @Value("${email.key}")
    private String key;

    private String sendEmail(){

        return null;
    }
}
