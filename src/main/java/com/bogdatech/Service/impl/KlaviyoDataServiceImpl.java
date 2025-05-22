package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.IKlaviyoDataService;
import com.bogdatech.entity.DO.KlaviyoDataDO;
import com.bogdatech.mapper.KlaviyoDataMapper;
import org.springframework.stereotype.Service;

import static com.bogdatech.constants.KlaviyoConstants.LIST;

@Service
public class KlaviyoDataServiceImpl extends ServiceImpl<KlaviyoDataMapper, KlaviyoDataDO> implements IKlaviyoDataService {

    @Override
    public Boolean saveProfile(KlaviyoDataDO klaviyoDataDO) {
        return baseMapper.insert(klaviyoDataDO) > 0;
    }

    @Override
    public String getListId(String listName) {
        return baseMapper.getListId(listName, LIST);
    }

    @Override
    public Boolean insertKlaviyoData(KlaviyoDataDO klaviyoDataDO) {
        return baseMapper.insertKlaviyoData(klaviyoDataDO.getShopName(), klaviyoDataDO.getName(), klaviyoDataDO.getType(), klaviyoDataDO.getStringId());
    }
}

