package com.bogdatech.logic;

import com.bogdatech.integration.PrivateIntegration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PrivateKeyService {
    private final PrivateIntegration privateIntegration;
    @Autowired
    public PrivateKeyService(PrivateIntegration privateIntegration) {
        this.privateIntegration = privateIntegration;
    }

    //测试google调用
    public void test(String text, String source, String apiKey, String target) {
        String s = privateIntegration.translateByGoogle(text, source, apiKey, target);
        System.out.println("s = " + s);
    }
}
