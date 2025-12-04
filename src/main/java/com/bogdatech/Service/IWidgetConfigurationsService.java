package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.DO.WidgetConfigurationsDO;

import java.util.List;

public interface IWidgetConfigurationsService extends IService<WidgetConfigurationsDO> {
    Boolean saveAndUpdateData(WidgetConfigurationsDO widgetConfigurationsDO);

    WidgetConfigurationsDO getData(String shopName);

    List<WidgetConfigurationsDO> getAllIpOpenByTrue();
}
