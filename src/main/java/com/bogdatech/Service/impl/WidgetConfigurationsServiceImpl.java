package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.IWidgetConfigurationsService;
import com.bogdatech.entity.WidgetConfigurationsDO;
import com.bogdatech.mapper.WidgetConfigurationsMapper;
import org.springframework.stereotype.Service;

@Service
public class WidgetConfigurationsServiceImpl extends ServiceImpl<WidgetConfigurationsMapper, WidgetConfigurationsDO> implements IWidgetConfigurationsService {
    @Override
    public Boolean saveAndUpdateData(WidgetConfigurationsDO widgetConfigurationsDO) {
        //先获取数据，如果没获取到数据，就插入。
        WidgetConfigurationsDO data = baseMapper.selectOne(new QueryWrapper<WidgetConfigurationsDO>().eq("shop_name", widgetConfigurationsDO.getShopName()));
        if (data == null) {
            return baseMapper.insert(widgetConfigurationsDO) > 0;
        }else {
            //获取到数据，就更新
            return baseMapper.update(widgetConfigurationsDO, new UpdateWrapper<WidgetConfigurationsDO>().eq("shop_name", widgetConfigurationsDO.getShopName())) > 0;
        }
    }

    @Override
    public WidgetConfigurationsDO getData(String shopName) {
        return baseMapper.selectOne(new QueryWrapper<WidgetConfigurationsDO>().eq("shop_name", shopName));
    }
}
