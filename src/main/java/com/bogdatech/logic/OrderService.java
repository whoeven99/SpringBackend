package com.bogdatech.logic;

import com.bogdatech.Service.ICharsOrdersService;
import com.bogdatech.Service.ITranslationCounterService;
import com.bogdatech.Service.IUsersService;
import com.bogdatech.entity.CharsOrdersDO;
import com.bogdatech.entity.UsersDO;
import com.bogdatech.integration.EmailIntegration;
import com.bogdatech.model.controller.request.PurchaseSuccessRequest;
import com.bogdatech.model.controller.request.TencentSendEmailRequest;
import com.microsoft.applicationinsights.TelemetryClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.bogdatech.constants.MailChimpConstants.CHARACTER_PURCHASE_SUCCESSFUL_SUBJECT;
import static com.bogdatech.constants.MailChimpConstants.TENCENT_FROM_EMAIL;

@Component
public class OrderService {
    private final ICharsOrdersService charsOrdersService;
    private final IUsersService usersService;
    private final EmailIntegration emailIntegration;
    private final ITranslationCounterService translationCounterService;
    TelemetryClient appInsights = new TelemetryClient();
    @Autowired
    public OrderService(ICharsOrdersService charsOrdersService, IUsersService usersService, EmailIntegration emailIntegration, ITranslationCounterService translationCounterService){
    this.charsOrdersService = charsOrdersService;
        this.usersService = usersService;
        this.emailIntegration = emailIntegration;
        this.translationCounterService = translationCounterService;
    }

    public Boolean insertOrUpdateOrder(CharsOrdersDO charsOrdersDO) {
        CharsOrdersDO charsOrdersServiceById = charsOrdersService.getById(charsOrdersDO.getId());
        if (charsOrdersServiceById == null) {
            return charsOrdersService.save(charsOrdersDO);
        }else {
            return charsOrdersService.updateStatusByShopName(charsOrdersDO.getId(), charsOrdersDO.getStatus());
        }
    }

    public List<String> getIdByShopName(String shopName) {
        return charsOrdersService.getIdByShopName(shopName);
    }

    public Boolean sendPurchaseSuccessEmail(PurchaseSuccessRequest purchaseSuccessRequest) {
        //根据shopName获取用户名
        UsersDO usersDO = usersService.getUserByName(purchaseSuccessRequest.getShopName());
        Map<String, String> templateData = new HashMap<>();
        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.US);
        String formattedNumber = formatter.format(purchaseSuccessRequest.getCredit());
        templateData.put("user", usersDO.getFirstName());
        templateData.put("number_of_credits", formattedNumber + " Credits");
        templateData.put("amount", String.format("%.2f", purchaseSuccessRequest.getAmount()) + " $");
        String suffix = ".myshopify.com";
        String TargetShop;
        TargetShop = purchaseSuccessRequest.getShopName().substring(0, purchaseSuccessRequest.getShopName().length() - suffix.length());
        templateData.put("shop_name", TargetShop);

        //获取用户现在总共的值
        Integer remainingChars = translationCounterService.getMaxCharsByShopName(purchaseSuccessRequest.getShopName());
        String formattedNumber2 = formatter.format(remainingChars + purchaseSuccessRequest.getCredit());
        templateData.put("total_credits_count", formattedNumber2 + " Credits");
//        return true;
        return emailIntegration.sendEmailByTencent(new TencentSendEmailRequest(138372L, templateData, CHARACTER_PURCHASE_SUCCESSFUL_SUBJECT, TENCENT_FROM_EMAIL, usersDO.getEmail()));
    }
}
