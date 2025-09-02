package com.bogdatech.logic;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bogdatech.Service.ICharsOrdersService;
import com.bogdatech.Service.IUserSubscriptionsService;
import com.bogdatech.Service.IUserTrialsService;
import com.bogdatech.entity.DO.CharsOrdersDO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserTrialsService {
    private final IUserTrialsService iUserTrialsService;
    private final IUserSubscriptionsService iUserSubscriptionsService;
    private final ICharsOrdersService iCharsOrdersService;

    @Autowired
    public UserTrialsService(IUserTrialsService iUserTrialsService, IUserSubscriptionsService iUserSubscriptionsService, ICharsOrdersService iCharsOrdersService) {
        this.iUserTrialsService = iUserTrialsService;
        this.iUserSubscriptionsService = iUserSubscriptionsService;
        this.iCharsOrdersService = iCharsOrdersService;
    }

    /**
     * 1,向免费订阅表里面插入用户信息
     * 2，修改用户订阅表，改为7 (这个暂时去除，目前没有用)
     * @param shopName 商店名称
     * @return Boolean 是否成功
     * */
    public Boolean insertUserTrial(String shopName){
        //        Boolean userSubscription = iUserSubscriptionsService.updateUserSubscription(shopName, 7);
        return iUserTrialsService.insertUserTrial(shopName);
    }

    /**
     * 1,给前端一个查询接口
     * @param shopName 商店名称
     * @return Boolean 是否成功
     * */
    public Boolean queryUserTrialByShopName(String shopName){
        //判断是否购买过订阅计划，如果有则返回true
        List<CharsOrdersDO> charsOrdersDOList = iCharsOrdersService.list(new QueryWrapper<CharsOrdersDO>().eq("shop_name", shopName).like("id", "AppSubscription").eq("status", "ACTIVE"));
        if (charsOrdersDOList != null && !charsOrdersDOList.isEmpty()){
            return true;
        }
        return iUserTrialsService.queryUserTrialByShopName(shopName);
    }
}
