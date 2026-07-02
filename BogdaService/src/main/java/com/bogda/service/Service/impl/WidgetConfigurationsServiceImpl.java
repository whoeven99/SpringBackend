package com.bogda.service.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.service.Service.IWidgetConfigurationsService;
import com.bogda.common.entity.DO.WidgetConfigurationsDO;
import com.bogda.service.mapper.WidgetConfigurationsMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WidgetConfigurationsServiceImpl extends ServiceImpl<WidgetConfigurationsMapper, WidgetConfigurationsDO> implements IWidgetConfigurationsService {
    @Override
    public List<WidgetConfigurationsDO> getAllIpOpenByTrue() {
        return baseMapper.selectList(new LambdaQueryWrapper<WidgetConfigurationsDO>().eq(WidgetConfigurationsDO::getIpOpen
                , true));
    }

    @Override
    public boolean updateIpTo0ByShopName(String shopName) {
        return baseMapper.update(new LambdaUpdateWrapper<WidgetConfigurationsDO>().eq(WidgetConfigurationsDO::getShopName, shopName)
                .set(WidgetConfigurationsDO::getIpOpen, false)) > 0;
    }
}
