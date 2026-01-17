package com.bogda.service.logic.PCApp;

import com.bogda.service.entity.DO.PCUsersDO;
import com.bogda.service.controller.response.BaseResponse;
import com.bogda.repository.entity.PCOrdersDO;
import com.bogda.repository.entity.PCUserSubscriptionsDO;
import com.bogda.repository.repo.PCOrdersRepo;
import com.bogda.repository.repo.PCSubscriptionsRepo;
import com.bogda.repository.repo.PCUserSubscriptionsRepo;
import com.bogda.service.PCUsersRepo;
import com.bogda.common.utils.AppInsightsUtils;
import com.bogda.common.utils.JsonUtils;
import com.bogda.common.utils.ShopifyUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

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
    @Autowired
    private PCUserSubscriptionsRepo pcUserSubscriptionsRepo;

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
            AppInsightsUtils.trackTrace("FatalException sendSubscribeSuccessEmail jsonNode is null : " + subscribeData);
            return new BaseResponse<>().CreateErrorResponse("subscribe data is null");
        }

        String status = jsonNode.path("app_subscription").path("status").asText(null);
        String subscribeId = jsonNode.path("app_subscription").path("admin_graphql_api_id").asText(null);
        String createdAt = jsonNode.path("app_subscription").path("created_at").asText(null);
        if (!"ACTIVE".equals(status) || subscribeId == null || createdAt == null) {
            AppInsightsUtils.trackTrace("status : " + status + " subscribeId : " + subscribeId + " createdAt : " + createdAt);
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

        // 获取订单类型 是年费还是月费 然后年费 + yearly
        PCUserSubscriptionsDO pcUserSubscriptionsByShopName = pcUserSubscriptionsRepo.getPcUserSubscriptionsByShopName(shopName);
        String planName = orderBySubGid.getName();
        if (pcUserSubscriptionsByShopName != null && pcUserSubscriptionsByShopName.getFeeType() == 1) {
            planName += " yearly";
        }

        String picChars = String.valueOf(charsByPlanName / 2000);
        pcEmailService.sendPcSubscribeEmail(user.getEmail(), user.getFirstName(), planName, picChars, createdAt);
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
            AppInsightsUtils.trackTrace("FatalException sendOneTimeBuySuccessEmail jsonNode is null : " + oneTimePurchaseData);
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
        String picChars = String.valueOf((user.getPurchasePoints() - user.getUsedPoints()) / 2000);
        String amountCount = String.valueOf(amount / 2000);
        pcEmailService.sendPcBuyEmail(user.getEmail(), user.getFirstName(), amountCount, picChars, utcTime);
        return new BaseResponse<>().CreateSuccessResponse(true);
    }
}
