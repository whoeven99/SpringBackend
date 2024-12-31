package com.bogdatech.logic;

import com.bogdatech.Service.ICharsOrdersService;
import com.bogdatech.Service.IUsersService;
import com.bogdatech.entity.CharsOrdersDO;
import com.bogdatech.entity.UsersDO;
import com.bogdatech.integration.EmailIntegration;
import com.bogdatech.model.controller.request.PurchaseSuccessRequest;
import com.bogdatech.model.controller.request.TencentSendEmailRequest;
import com.microsoft.applicationinsights.TelemetryClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.bogdatech.constants.MailChimpConstants.CHARACTER_PURCHASE_SUCCESSFUL_SUBJECT;
import static com.bogdatech.constants.MailChimpConstants.TENCENT_FROM_EMAIL;

@Component
public class OrderService {
    private final ICharsOrdersService charsOrdersService;
    private final IUsersService usersService;
    private final EmailIntegration emailIntegration;
    TelemetryClient appInsights = new TelemetryClient();
    @Autowired
    public OrderService(ICharsOrdersService charsOrdersService, IUsersService usersService, EmailIntegration emailIntegration){
    this.charsOrdersService = charsOrdersService;
        this.usersService = usersService;
        this.emailIntegration = emailIntegration;
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
        templateData.put("user", usersDO.getFirstName());
//        templateData.put("order", purchaseSuccessRequest.getOrder());
        //TODO: 完善变量值
        return emailIntegration.sendEmailByTencent(new TencentSendEmailRequest(133297L, templateData, CHARACTER_PURCHASE_SUCCESSFUL_SUBJECT, TENCENT_FROM_EMAIL, usersDO.getEmail()));
    }
}
