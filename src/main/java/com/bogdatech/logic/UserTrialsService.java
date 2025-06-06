package com.bogdatech.logic;

import com.bogdatech.Service.IUserSubscriptionsService;
import com.bogdatech.Service.IUserTrialsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserTrialsService {
    private final IUserTrialsService iUserTrialsService;
    private final IUserSubscriptionsService iUserSubscriptionsService;

    @Autowired
    public UserTrialsService(IUserTrialsService iUserTrialsService, IUserSubscriptionsService iUserSubscriptionsService) {
        this.iUserTrialsService = iUserTrialsService;
        this.iUserSubscriptionsService = iUserSubscriptionsService;
    }

    /**
     * 1,向免费订阅表里面插入用户信息
     * 2，修改用户订阅表，改为7
     * @param shopName 商店名称
     * @return Boolean 是否成功
     * */
    public Boolean insertUserTrial(String shopName){
        boolean insertUserTrial = iUserTrialsService.insertUserTrial(shopName);
        Boolean userSubscription = iUserSubscriptionsService.updateUserSubscription(shopName, 7);
        return insertUserTrial && userSubscription;
    }
}
