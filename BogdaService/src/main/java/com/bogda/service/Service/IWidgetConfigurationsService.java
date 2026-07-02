package com.bogda.service.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogda.common.entity.DO.WidgetConfigurationsDO;

import java.util.List;

public interface IWidgetConfigurationsService extends IService<WidgetConfigurationsDO> {
    List<WidgetConfigurationsDO> getAllIpOpenByTrue();

    boolean updateIpTo0ByShopName(String shopName);
}
