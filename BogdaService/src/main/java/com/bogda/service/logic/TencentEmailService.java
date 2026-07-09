package com.bogda.service.logic;

import com.bogda.common.entity.DO.*;
import com.bogda.common.reporter.TraceReporterHolder;
import com.bogda.common.utils.StringUtils;
import com.bogda.service.Service.*;
import com.bogda.service.integration.EmailIntegration;
import com.bogda.common.controller.request.TencentSendEmailRequest;
import com.bogda.common.contants.MailChimpConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Component
public class TencentEmailService {
    @Autowired
    private EmailIntegration emailIntegration;
    @Autowired
    private IEmailService emailService;
    @Autowired
    private IUsersService usersService;
    @Autowired
    private ITranslationCounterService translationCounterService;
    @Autowired
    private IAPGEmailService iapgEmailService;

    /**
     * 发送订阅计划付费后的邮件
     */
    public void sendSubscribeEmail(String shopName, Integer numberOfCredits) {
        UsersDO usersDO = usersService.getUserByName(shopName);
        Map<String, String> templateData = new HashMap<>();
        templateData.put("user", usersDO.getFirstName());

        // 定义要移除的后缀
        String suffix = ".myshopify.com";
        String targetShop;
        targetShop = shopName.substring(0, shopName.length() - suffix.length());
        templateData.put("shop_name", targetShop);

        // 获得用户总的token数
        TranslationCounterDO translationCounterDO = translationCounterService.readCharsByShopName(shopName);
        Integer maxCharsByShopName = translationCounterService.getMaxCharsByShopName(shopName);
        Integer totalCreditsCount = maxCharsByShopName - translationCounterDO.getUsedChars();
        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.US);
        templateData.put("number_of_credits", formatter.format(numberOfCredits));
        templateData.put("total_credits_count", formatter.format(totalCreditsCount));

        // 由腾讯发送邮件
        Boolean b = emailIntegration.sendEmailByTencent(new TencentSendEmailRequest(143058L, templateData,
                MailChimpConstants.SUBSCRIBE_SUCCESSFUL_SUBJECT, MailChimpConstants.TENCENT_FROM_EMAIL, usersDO.getEmail()));

        // 存入数据库中
        emailService.saveEmail(new EmailDO(0, shopName, MailChimpConstants.TENCENT_FROM_EMAIL, usersDO.getEmail(),
                MailChimpConstants.SUBSCRIBE_SUCCESSFUL_SUBJECT, b ? 1 : 0));
    }

    /**
     * 发送APG应用初始化邮件
     *
     * @param email  接收人的邮箱
     * @param userId 接收人的商店id
     */
    public void sendApgInitEmail(String email, Long userId) {
        //由腾讯发送邮件
        Boolean b = emailIntegration.sendEmailByTencent(new TencentSendEmailRequest(144208L, null,
                MailChimpConstants.APG_INIT_EMAIL, MailChimpConstants.TENCENT_FROM_EMAIL, email));
        //存入数据库中
        iapgEmailService.saveEmail(new APGEmailDO(null, userId, MailChimpConstants.TENCENT_FROM_EMAIL, email,
                MailChimpConstants.APG_INIT_EMAIL, b));
    }

    /**
     * 发送生成描述成功的邮件 144209
     *
     * @param email  接收人的邮箱
     * @param userId 接收人的商店id
     */
    public void sendAPGSuccessEmail(String email, Long userId, String taskType, String userName, Timestamp createTime, Integer totalToken, Integer products, Integer remaining) {
        //根据用户的id获取生成模块的相关数据
        Map<String, String> templateData = new HashMap<>();
        templateData.put("task_type", taskType); // taskType: product, collection
        templateData.put("username", userName);
        templateData.put("product_count", String.valueOf(products));
        Instant now = Instant.now();
        Instant created = createTime.toInstant();

        long seconds = Duration.between(created, now).getSeconds();
        templateData.put("duration", String.valueOf(seconds));
        //将下面数据改为千分位
        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.US);
        templateData.put("credit_used", formatter.format(totalToken));
        templateData.put("credit_remaining", formatter.format(remaining));
        //设置参数
        Boolean b = emailIntegration.sendEmailByTencent(new TencentSendEmailRequest(144209L, templateData,
                MailChimpConstants.APG_GENERATE_SUCCESS, MailChimpConstants.TENCENT_FROM_EMAIL, email));
        //存入数据库中
        iapgEmailService.saveEmail(new APGEmailDO(null, userId, MailChimpConstants.TENCENT_FROM_EMAIL, email,
                MailChimpConstants.APG_GENERATE_SUCCESS, b));
    }

    /**
     * 发送APG应用购买成功的邮件
     */
    public Integer sendAPGPurchaseEmail(APGUsersDO apgUsersDO, Integer numberOfCredits, Double creditAmount, Integer creditBalance) {
        Map<String, String> templateData = new HashMap<>();
        templateData.put("username", apgUsersDO.getFirstName());
        // 获得用户总的token数
        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.US);
        templateData.put("purchase_amount", formatter.format(numberOfCredits));
        templateData.put("credit_amount", formatter.format(creditAmount));
        templateData.put("credit_balance", formatter.format(creditBalance));
        //由腾讯发送邮件
        Boolean b = emailIntegration.sendEmailByTencent(new TencentSendEmailRequest(144922L, templateData,
                MailChimpConstants.APG_PURCHASE_EMAIL, MailChimpConstants.TENCENT_FROM_EMAIL, apgUsersDO.getEmail()));
        //存入数据库中
        return iapgEmailService.saveEmail(new APGEmailDO(null, apgUsersDO.getId(), MailChimpConstants.TENCENT_FROM_EMAIL,
                apgUsersDO.getEmail(), MailChimpConstants.APG_PURCHASE_EMAIL, b)) ? 1 : 0;
    }

    /**
     * 发送APG应用任务中断的邮件
     */
    public void sendAPGTaskInterruptEmail(APGUsersDO apgUsersDO, Integer completedCount, Integer remainingCount, Integer creditBalance) {
        Map<String, String> templateData = new HashMap<>();
        templateData.put("username", apgUsersDO.getFirstName());
        //存用户翻译任务等数据
        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.US);
        templateData.put("credit_balance", formatter.format(creditBalance));
        templateData.put("completed_count", String.valueOf(completedCount));
        templateData.put("remaining_count", String.valueOf(remainingCount));
        //由腾讯发送邮件
        Boolean b = emailIntegration.sendEmailByTencent(new TencentSendEmailRequest(144923L, templateData,
                MailChimpConstants.APG_TASK_INTERRUPT_EMAIL, MailChimpConstants.TENCENT_FROM_EMAIL, apgUsersDO.getEmail()));
        //存入数据库中
        iapgEmailService.saveEmail(new APGEmailDO(null, apgUsersDO.getId(), MailChimpConstants.TENCENT_FROM_EMAIL,
                apgUsersDO.getEmail(), MailChimpConstants.APG_TASK_INTERRUPT_EMAIL, b));
    }

    public Boolean sendInitialUserEmail(long emailId, Map<String, String> templateData, String firstInstallSubject, String tencentFromEmail, String email) {
        return emailIntegration.sendEmailByTencent(new TencentSendEmailRequest(emailId,
                templateData, firstInstallSubject, tencentFromEmail, email));
    }

}
