package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.KlaviyoDataDO;

public interface IKlaviyoDataService extends IService<KlaviyoDataDO> {
    //存储用户的profile信息
    public Boolean saveProfile(KlaviyoDataDO klaviyoDataDO);

    String getListId(String listName);
}
