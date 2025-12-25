package com.bogda.common.logic;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bogda.common.service.*;
import com.bogda.common.constants.MailChimpConstants;
import com.bogda.common.constants.TranslateConstants;
import com.bogda.common.entity.DO.*;
import com.bogda.common.integration.EmailIntegration;
import com.bogda.common.model.controller.request.PurchaseSuccessRequest;
import com.bogda.common.model.controller.request.TencentSendEmailRequest;
import com.bogda.common.utils.CaseSensitiveUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class OrderService {
    @Autowired
    private ICharsOrdersService charsOrdersService;
    @Autowired
    private IUsersService usersService;
    @Autowired
    private EmailIntegration emailIntegration;
    @Autowired
    private ITranslationCounterService translationCounterService;
    @Autowired
    private IUserTrialsService iUserTrialsService;
    @Autowired
    private IUserSubscriptionsService iUserSubscriptionsService;


    public Boolean insertOrUpdateOrder(CharsOrdersDO charsOrdersDO) {
        CharsOrdersDO charsOrdersServiceById = charsOrdersService.getById(charsOrdersDO.getId());
        if (charsOrdersServiceById == null) {
            return charsOrdersService.save(charsOrdersDO);
        } else {
            return charsOrdersService.updateStatusByShopName(charsOrdersDO.getId(), charsOrdersDO.getStatus());
        }
    }

    public Boolean sendPurchaseSuccessEmail(PurchaseSuccessRequest purchaseSuccessRequest) {
        //根据shopName获取用户名
        UsersDO usersDO = usersService.getUserByName(purchaseSuccessRequest.getShopName());
        Map<String, String> templateData = new HashMap<>();
        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.US);
        String formattedNumber = formatter.format(purchaseSuccessRequest.getCredit());
        templateData.put("user", usersDO.getFirstName());
        templateData.put("number_of_credits", formattedNumber + " Credits");
        templateData.put("amount", "$" + String.format("%.2f", purchaseSuccessRequest.getAmount()));
        String suffix = ".myshopify.com";
        String targetShop;
        targetShop = purchaseSuccessRequest.getShopName().substring(0, purchaseSuccessRequest.getShopName().length() - suffix.length());
        templateData.put("shop_name", targetShop);

        //获取用户现在总共的值
        Integer remainingChars = translationCounterService.getMaxCharsByShopName(purchaseSuccessRequest.getShopName());
        //获取用户已使用的token值
        Integer usedChars = translationCounterService.getOne(new QueryWrapper<TranslationCounterDO>().eq("shop_name"
                , purchaseSuccessRequest.getShopName())).getUsedChars();
        //当购买的token大于相减的token，展示购买的token
        if (purchaseSuccessRequest.getCredit() > (remainingChars - usedChars)) {
            templateData.put("total_credits_count", formattedNumber + " Credits");
        } else {
            String formattedNumber2 = formatter.format(remainingChars - usedChars);
            templateData.put("total_credits_count", formattedNumber2 + " Credits");
        }
        return emailIntegration.sendEmailByTencent(new TencentSendEmailRequest(138372L, templateData,
                MailChimpConstants.CHARACTER_PURCHASE_SUCCESSFUL_SUBJECT, MailChimpConstants.TENCENT_FROM_EMAIL, usersDO.getEmail()));
    }

    public Boolean sendSubscribeSuccessEmail(String shopName, String subId, int feeType) {
        //判断是否是免费试用,根据用户额度的数据查看
        UserTrialsDO userTrialsDO = iUserTrialsService.getOne(new LambdaQueryWrapper<UserTrialsDO>().eq(UserTrialsDO::getShopName, shopName));
        //修改用户计划表里面用户feeType
        boolean update = iUserSubscriptionsService.update(new LambdaUpdateWrapper<UserSubscriptionsDO>()
                .eq(UserSubscriptionsDO::getShopName, shopName).set(UserSubscriptionsDO::getFeeType, feeType));
        CaseSensitiveUtils.appInsights.trackTrace("sendSubscribeSuccessEmail 用户 " + shopName + " 修改用户计划表里面用户feeType "
                + update + " feeType为" + feeType + " subId为" + subId);
        //根据shopName获取用户名
        UsersDO usersDO = usersService.getUserByName(shopName);
        //根据shopName获取订单信息
        CharsOrdersDO userData = charsOrdersService.getOne(new LambdaQueryWrapper<CharsOrdersDO>().eq(CharsOrdersDO::getId, subId));
        if (userData == null) {
            return false;
        }
        if (userTrialsDO != null && !userTrialsDO.getIsTrialExpired()) {
            //发送免费试用邮件
            Map<String, String> templateData = new HashMap<>();
            templateData.put("user", usersDO.getFirstName());
            templateData.put("new_plan_name", userData.getName());
            //从免费试用表里面获取对应开始和结束时间
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String trialStart = sdf.format(userTrialsDO.getTrialStart());
            String trialEnd = sdf.format(userTrialsDO.getTrialEnd());
            templateData.put("Start date", trialStart + " UTC");
            templateData.put("End date", trialEnd + " UTC");
            emailIntegration.sendEmailByTencent(new TencentSendEmailRequest(146220L, templateData,
                    MailChimpConstants.PLAN_TRIALS_SUCCESSFUL, MailChimpConstants.TENCENT_FROM_EMAIL, usersDO.getEmail()));
            CaseSensitiveUtils.appInsights.trackTrace("sendSubscribeSuccessEmail: " + shopName + " is free trial");
            return false;
        }
        Map<String, String> templateData = new HashMap<>();
        templateData.put("user", usersDO.getFirstName());
        templateData.put("new_plan_name", userData.getName());
        templateData.put("new_fee", "$" + userData.getAmount());
        // 定义输出格式 (不带 T 的格式)
        DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        // 格式化为目标格式
        String formattedDateTime = userData.getCreatedAt().format(outputFormatter);
        templateData.put("effective_date", formattedDateTime + " UTC");
        String suffix = ".myshopify.com";
        String targetShop;
        targetShop = usersDO.getShopName().substring(0, usersDO.getShopName().length() - suffix.length());
        templateData.put("shop_name", targetShop);

        if (feeType == TranslateConstants.MONTHLY_FEE) {
            return emailIntegration.sendEmailByTencent(new TencentSendEmailRequest(139251L, templateData,
                    MailChimpConstants.PLAN_UPGRADE_SUCCESSFUL, MailChimpConstants.TENCENT_FROM_EMAIL, usersDO.getEmail()));
        } else if (feeType == TranslateConstants.ANNUAL_FEE) {
            double value = userData.getAmount() * 12;
            String formatted = String.format("%.2f", value);
            templateData.put("new_fee", "$" + formatted);
            return emailIntegration.sendEmailByTencent(new TencentSendEmailRequest(146081L, templateData,
                    MailChimpConstants.PLAN_UPGRADE_SUCCESSFUL, MailChimpConstants.TENCENT_FROM_EMAIL, usersDO.getEmail()));
        }
        return false;
    }

    public String getLatestActiveSubscribeId(String shopName) {
        List<CharsOrdersDO> charsOrders = charsOrdersService.list(new LambdaQueryWrapper<CharsOrdersDO>()
                        .eq(CharsOrdersDO::getShopName, shopName)
                        .eq(CharsOrdersDO::getStatus, "ACTIVE")
                        .orderByDesc(CharsOrdersDO::getCreatedAt)
                ).stream().filter(order -> order.getId() != null && order.getId().contains("AppSubscription"))
                .toList();
        if (charsOrders.isEmpty()){
            // 返回null
            return null;
        }

        CharsOrdersDO charsOrder = charsOrders.get(0);
        if (charsOrder != null) {
            return charsOrder.getId();
        }
        return null;

    }
}
