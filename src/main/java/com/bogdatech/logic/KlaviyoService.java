package com.bogdatech.logic;

import com.bogdatech.Service.IKlaviyoDataService;
import com.bogdatech.entity.DO.KlaviyoDataDO;
import com.bogdatech.entity.DO.UsersDO;
import com.bogdatech.integration.KlaviyoIntegration;
import com.bogdatech.model.controller.request.ProfileToListRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class KlaviyoService {
    @Autowired
    private KlaviyoIntegration klaviyoIntegration;

    @Autowired
    private IKlaviyoDataService klaviyoDataService;

    //创建profile
    public String createProfile(UsersDO usersDO) {
        return klaviyoIntegration.createProfile(usersDO);
    }

    //将profile存进list里面
    public Boolean addProfileToKlaviyoList(ProfileToListRequest request) {
        return klaviyoIntegration.addProfileToKlaviyoList(request.getProfileId(), request.getListId());
    }

   //将profile存入数据库中
    public Boolean addProfileToDatabase(KlaviyoDataDO klaviyoDataDO) {
        return klaviyoDataService.insertKlaviyoData(klaviyoDataDO);
    }

    //获取listId
    public String getListId(String listName) {
        return klaviyoDataService.getListId(listName);
    }
}
