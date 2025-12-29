package com.bogda.api.logic.PCApp;

import com.bogda.api.constants.MailChimpConstants;
import com.bogda.api.integration.EmailIntegration;
import com.bogda.api.utils.ShopifyUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

@Component
public class PCEmailService {
    @Autowired
    private EmailIntegration emailIntegration;

    /**
     * 发送图片翻译初始化api
     */
    public void sendPcInitialEmail(String email, String userName) {
        // 发送邮件
        Map<String, String> templateData = new HashMap<>();
        templateData.put("name", userName);
        emailIntegration.sendEmailByTencent(158999L, MailChimpConstants.PC_FIRST_INSTALL_EMAIL, templateData, MailChimpConstants.TENCENT_FROM_EMAIL, email);
    }

    /**
     * 发送购买额度邮件
     */
    public void sendPcBuyEmail(String email, String userName, String purchaseCredits, String totalCredits, String purchaseTime) {
        Map<String, String> templateData = new HashMap<>();
        templateData.put("name", userName);
        templateData.put("credits_purchased", ShopifyUtils.getNumberFormat(purchaseCredits));
        templateData.put("credits_total", ShopifyUtils.getNumberFormat(totalCredits));
        templateData.put("purchase_time", purchaseTime);
        System.out.println("templateData: " + templateData);
        emailIntegration.sendEmailByTencent(159001L, MailChimpConstants.PC_PURCHASE_EMAIL, templateData, MailChimpConstants.TENCENT_FROM_EMAIL, email);
    }

    /**
     * 发送计划订阅成功邮件
     */
    public void sendPcSubscribeEmail(String email, String userName, String subscriptionPlan, String picLimit, String purchaseTime) {
        Map<String, String> templateData = new HashMap<>();
        templateData.put("name", userName);
        templateData.put("plan_name", subscriptionPlan);
        templateData.put("image_limit", ShopifyUtils.getNumberFormat(picLimit));

        // 修改时间格式 加30天
        Instant instant = Instant.parse(purchaseTime);

        // 加 30 天
        Instant plus30Days = instant.plus(30, ChronoUnit.DAYS);
        String activationDate = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC).format(instant);
        String nextBillingDate = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC).format(plus30Days);
        templateData.put("activation_date", activationDate);
        templateData.put("next_billing_date", nextBillingDate);
        emailIntegration.sendEmailByTencent(159003L, MailChimpConstants.PC_PLAN_SUBSCRIBE_SUCCESSFUL, templateData, MailChimpConstants.TENCENT_FROM_EMAIL, email);
    }

    /**
     *  发送每月邮件额度赠送邮件
     */
    public void sendPcFreeEmail(String email, String userName, String subscriptionPlan, String picLimit, String allPicLimit) {
        Map<String, String> templateData = new HashMap<>();
        templateData.put("name", userName);
        templateData.put("plan_name", subscriptionPlan);
        templateData.put("new_cycle_images", ShopifyUtils.getNumberFormat(picLimit));
        templateData.put("current_image_balance", ShopifyUtils.getNumberFormat(allPicLimit));
        emailIntegration.sendEmailByTencent(159005L, MailChimpConstants.PC_PLAN_UPGRADE_SUCCESSFUL, templateData, MailChimpConstants.TENCENT_FROM_EMAIL, email);
    }
}
