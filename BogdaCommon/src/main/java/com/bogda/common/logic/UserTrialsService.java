package com.bogda.common.logic;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bogda.common.Service.ICharsOrdersService;
import com.bogda.common.Service.IUserTrialsService;
import com.bogda.common.entity.DO.CharsOrdersDO;
import com.bogda.common.entity.DO.UserTrialsDO;
import com.bogda.common.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static com.bogda.common.utils.CaseSensitiveUtils.appInsights;

@Service
public class UserTrialsService {
    @Autowired
    private IUserTrialsService iUserTrialsService;
    @Autowired
    private ICharsOrdersService iCharsOrdersService;

    /**
     * 1,向免费订阅表里面插入用户信息
     * 2，修改用户订阅表，改为7 (这个暂时去除，目前没有用)
     */
    public Boolean insertUserTrial(String shopName) {
        return iUserTrialsService.insertUserTrial(shopName);
    }

    /**
     * 1,给前端一个查询接口
     */
    public BaseResponse<Object> queryUserTrialByShopName(String shopName) {
        //判断是否购买过订阅计划，如果有则返回true
        List<CharsOrdersDO> charsOrdersDOList = iCharsOrdersService.list(new QueryWrapper<CharsOrdersDO>().eq("shop_name", shopName)
                .eq("status", "ACTIVE"))
                .stream().filter(data -> data.getShopName() != null && data.getId().contains("AppSubscription")).toList();
        if (!charsOrdersDOList.isEmpty()) {
            appInsights.trackTrace("queryUserTrialByShopName " + shopName + " 返回的charsOrdersDOList 值为 ： " + charsOrdersDOList);
            return new BaseResponse<>().CreateErrorResponse(charsOrdersDOList);
        }
        Boolean flag = iUserTrialsService.queryUserTrialByShopName(shopName);
        if (flag != null) {
            return new BaseResponse<>().CreateSuccessResponse(flag);
        }
        return new BaseResponse<>().CreateErrorResponse(false);
    }

    public BaseResponse<Object> isShowFreePlan(String shopName) {
        //从数据库中获取是否弹出免费试用的弹窗
        UserTrialsDO userTrialsDO = iUserTrialsService.getOne(new LambdaQueryWrapper<UserTrialsDO>().eq(UserTrialsDO::getShopName, shopName));
        if (userTrialsDO != null && !userTrialsDO.getIsTrialShow()) {
            //将弹窗状态改为true
            boolean update = iUserTrialsService.update(new LambdaUpdateWrapper<UserTrialsDO>().eq(UserTrialsDO::getShopName, shopName).set(UserTrialsDO::getIsTrialShow, true));
            if (update) {
                return new BaseResponse<>().CreateSuccessResponse(true);
            }
            return new BaseResponse<>().CreateErrorResponse("db error");

        }
        return new BaseResponse<>().CreateSuccessResponse(false);

    }

    public BaseResponse<Object> isInFreePlanTime(String shopName) {
        // 获取该用户是否在免费试用期间
        Timestamp now = Timestamp.from(Instant.now());

        UserTrialsDO userTrialsDO = iUserTrialsService.getOne(new LambdaQueryWrapper<UserTrialsDO>().eq(UserTrialsDO::getShopName, shopName));
        // 判断now是否在trialStart 和 trialEnd 中间.  是，返回true； 否，返回false
        if (userTrialsDO != null && userTrialsDO.getTrialStart().before(now) && userTrialsDO.getTrialEnd().after(now) && !userTrialsDO.getIsTrialExpired()) {
            return new BaseResponse<>().CreateSuccessResponse(true);
        }
        return new BaseResponse<>().CreateSuccessResponse(false);
    }
}
