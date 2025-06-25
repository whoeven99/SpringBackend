package com.bogdatech.logic;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bogdatech.Service.ICharsOrdersService;
import com.bogdatech.Service.ITranslationCounterService;
import com.bogdatech.Service.IUsersService;
import com.bogdatech.entity.DO.CharsOrdersDO;
import com.bogdatech.entity.DO.TranslationCounterDO;
import com.bogdatech.entity.DO.UsersDO;
import com.bogdatech.integration.EmailIntegration;
import com.bogdatech.model.controller.request.PurchaseSuccessRequest;
import com.bogdatech.model.controller.request.TencentSendEmailRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.bogdatech.constants.MailChimpConstants.*;

@Component
public class OrderService {
    private final ICharsOrdersService charsOrdersService;
    private final IUsersService usersService;
    private final EmailIntegration emailIntegration;
    private final ITranslationCounterService translationCounterService;

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
        templateData.put("amount", "$" + String.format("%.2f", purchaseSuccessRequest.getAmount()));
        String suffix = ".myshopify.com";
        String targetShop;
        targetShop = purchaseSuccessRequest.getShopName().substring(0, purchaseSuccessRequest.getShopName().length() - suffix.length());
        templateData.put("shop_name", targetShop);

        //获取用户现在总共的值
        Integer remainingChars = translationCounterService.getMaxCharsByShopName(purchaseSuccessRequest.getShopName());
        //获取用户已使用的token值
        Integer usedChars = translationCounterService.getOne(new QueryWrapper<TranslationCounterDO>().eq("shop_name", purchaseSuccessRequest.getShopName())).getUsedChars();
        String formattedNumber2 = formatter.format(remainingChars-usedChars);
        templateData.put("total_credits_count", formattedNumber2 + " Credits");
//        return true;
        return emailIntegration.sendEmailByTencent(new TencentSendEmailRequest(138372L, templateData, CHARACTER_PURCHASE_SUCCESSFUL_SUBJECT, TENCENT_FROM_EMAIL, usersDO.getEmail()));
    }

    public Boolean sendSubscribeSuccessEmail(CharsOrdersDO charsOrdersDO) {
        //根据shopName获取用户名
        UsersDO usersDO = usersService.getUserByName(charsOrdersDO.getShopName());
        //根据shopName获取订单信息
        CharsOrdersDO userData = charsOrdersService.getOne(new QueryWrapper<CharsOrdersDO>().eq("id", charsOrdersDO.getId()));
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

        return emailIntegration.sendEmailByTencent(new TencentSendEmailRequest(139251L, templateData, PLAN_UPGRADE_SUCCESSFUL, TENCENT_FROM_EMAIL, usersDO.getEmail()));
    }
}
