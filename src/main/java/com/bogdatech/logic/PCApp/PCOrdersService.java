package com.bogdatech.logic.PCApp;

import com.bogdatech.entity.DO.PCUsersDO;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.repository.entity.PCOrdersDO;
import com.bogdatech.repository.repo.PCOrdersRepo;
import com.bogdatech.repository.repo.PCSubscriptionsRepo;
import com.bogdatech.repository.repo.PCUsersRepo;
import com.bogdatech.utils.JsonUtils;
import com.bogdatech.utils.ShopifyUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

@Component
public class PCOrdersService {
    @Autowired
    private PCOrdersRepo pcOrdersRepo;
    @Autowired
    private PCEmailService pcEmailService;
    @Autowired
    private PCUsersRepo pcUsersRepo;
    @Autowired
    private PCSubscriptionsRepo pcSubscriptionsRepo;

    public BaseResponse<Object> insertOrUpdateOrder(PCOrdersDO pcOrdersDO) {
        PCOrdersDO selectOrder = pcOrdersRepo.getOrderByOrderId(pcOrdersDO.getOrderId());
        boolean result;

        if (selectOrder == null) {
            // 新增
            result = pcOrdersRepo.save(pcOrdersDO);
            return result
                    ? new BaseResponse<>().CreateSuccessResponse(true)
                    : new BaseResponse<>().CreateErrorResponse("save error");
        } else {
            // 更新
            result = pcOrdersRepo.updateStatusByShopName(selectOrder.getId(), pcOrdersDO.getStatus());
            return result
                    ? new BaseResponse<>().CreateSuccessResponse(true)
                    : new BaseResponse<>().CreateErrorResponse("update error");
        }
    }

    public BaseResponse<Object> getLatestActiveSubscribeId(String shopName) {
        String latestActiveSubscribeId = pcOrdersRepo.getLatestActiveSubscribeId(shopName);
        if (latestActiveSubscribeId != null) {
            return new BaseResponse<>().CreateSuccessResponse(latestActiveSubscribeId);
        }
        return new BaseResponse<>().CreateErrorResponse("no active subscribe");
    }

    public BaseResponse<Object> sendSubscribeSuccessEmail(String shopName, String subscribeData) {
        try {
            Thread.sleep(3000); // 睡眠 3 秒（3000 毫秒）
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // 推荐写法
        }

        // 解析计划数据，如果是Active，发送邮件，反之不发送
        JsonNode jsonNode = JsonUtils.readTree(subscribeData);
        if (jsonNode == null) {
            appInsights.trackTrace("FatalException sendSubscribeSuccessEmail jsonNode is null : " + subscribeData);
            return new BaseResponse<>().CreateErrorResponse("subscribe data is null");
        }

        String status = jsonNode.path("app_subscription").path("status").asText(null);
        String subscribeId = jsonNode.path("app_subscription").path("admin_graphql_api_id").asText(null);
        String createdAt = jsonNode.path("app_subscription").path("created_at").asText(null);
        if (!"ACTIVE".equals(status) || subscribeId == null || createdAt == null) {
            System.out.println("status : " + status + " subscribeId : " + subscribeId + " createdAt : " + createdAt);
            return new BaseResponse<>().CreateErrorResponse("subscribe status is not Active");
        }

        PCUsersDO user = pcUsersRepo.getUserByShopName(shopName);
        PCOrdersDO orderBySubGid = pcOrdersRepo.getOrderBySubGid(subscribeId);
        if (orderBySubGid == null) {
            return new BaseResponse<>().CreateErrorResponse("no order by subscribe id: " + subscribeId + " shopName : " + shopName);
        }

        Integer charsByPlanName = pcSubscriptionsRepo.getCharsByPlanName(orderBySubGid.getName());
        if (charsByPlanName == null) {
            return new BaseResponse<>().CreateErrorResponse("no chars by plan name: " + orderBySubGid.getName());
        }


        String picChars = String.valueOf(charsByPlanName / 2000);
        pcEmailService.sendPcSubscribeEmail(user.getEmail(), user.getFirstName(), orderBySubGid.getName(), picChars, createdAt);
        return new BaseResponse<>().CreateSuccessResponse(true);
    }

    public BaseResponse<Object> sendOneTimeBuySuccessEmail(String shopName, String oneTimePurchaseData) {
        // 睡眠3s
        try {
            Thread.sleep(3000); // 睡眠 3 秒（3000 毫秒）
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // 推荐写法
        }

        // 解析一次性购买数据， 如果是Active，发送邮件，反之不发送
        JsonNode jsonNode = JsonUtils.readTree(oneTimePurchaseData);
        if (jsonNode == null) {
            appInsights.trackTrace("FatalException sendOneTimeBuySuccessEmail jsonNode is null : " + oneTimePurchaseData);
            return new BaseResponse<>().CreateErrorResponse("subscribe data is null");
        }

        String status = jsonNode.path("app_purchase_one_time").path("status").asText(null);
        String subscribeId = jsonNode.path("app_purchase_one_time").path("admin_graphql_api_id").asText(null);
        String createdAt = jsonNode.path("app_purchase_one_time").path("created_at").asText(null);
        String name = jsonNode.path("app_purchase_one_time").path("name").asText(null);
        if (!"ACTIVE".equals(status) || subscribeId == null || createdAt == null || name == null) {
            return new BaseResponse<>().CreateErrorResponse("subscribe status is not Active");
        }

        PCUsersDO user = pcUsersRepo.getUserByShopName(shopName);

        // 根据name 获取对应的额度
        Integer amount = ShopifyUtils.getAmount(name);
        if (amount == null) {
            return new BaseResponse<>().CreateErrorResponse("no chars by plan name: " + user);
        }

        String utcTime = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                .withZone(ZoneOffset.UTC)
                .format(Instant.parse(createdAt));
        String picChars = String.valueOf(user.getPurchasePoints() - user.getUsedPoints());
        System.out.println(picChars);
        pcEmailService.sendPcBuyEmail(user.getEmail(), user.getFirstName(), String.valueOf(amount), picChars, utcTime);
        return new BaseResponse<>().CreateSuccessResponse(true);
    }

    public BaseResponse<Object> test(String shopName) {
        // 满足条件，执行添加字符的逻辑
        // 根据计划获取对应的字符
        Integer chars = pcSubscriptionsRepo.getCharsByPlanName("Basic");
        if (chars == null) {
            appInsights.trackTrace("FatalException test chars is null : " + shopName);
            return new BaseResponse<>().CreateErrorResponse("no active subscribe");
        }

        // 发送邮件通知
        PCUsersDO userByShopName = pcUsersRepo.getUserByShopName(shopName);

        // 发送对应的邮件
        // 计算token转化为图片张数 除于2000
        String planPicChars = String.valueOf(chars / 2000);
        String allPicLimit = (userByShopName.getPurchasePoints() - userByShopName.getUsedPoints()) / 2000 + "";

        pcEmailService.sendPcFreeEmail(userByShopName.getEmail(), userByShopName.getFirstName(), "Basic", planPicChars, allPicLimit);
        return null;
    }
}
