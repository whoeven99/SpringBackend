package com.bogda.api.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.api.Service.IWidgetConfigurationsService;
import com.bogda.api.entity.DO.WidgetConfigurationsDO;
import com.bogda.api.mapper.WidgetConfigurationsMapper;
import org.springframework.stereotype.Service;

import java.util.List;

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
        return baseMapper.selectOne(new LambdaQueryWrapper<WidgetConfigurationsDO>().eq(WidgetConfigurationsDO::getShopName, shopName));
    }

    @Override
    public List<WidgetConfigurationsDO> getAllIpOpenByTrue() {
        return baseMapper.selectList(new LambdaQueryWrapper<WidgetConfigurationsDO>().eq(WidgetConfigurationsDO::getIpOpen
                , true));
    }
}
