package com.bogdatech.controller;

import com.bogdatech.Service.IWidgetConfigurationsService;
import com.bogdatech.entity.DO.WidgetConfigurationsDO;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

@RestController
@RequestMapping("/widgetConfigurations")
public class WidgetConfigurationsController {

    private final IWidgetConfigurationsService widgetConfigurationsService;

    @Autowired
    public WidgetConfigurationsController(IWidgetConfigurationsService widgetConfigurationsService) {
        this.widgetConfigurationsService = widgetConfigurationsService;
    }

    //供前端插入和更新数据API
    @PostMapping("/saveAndUpdateData")
    public BaseResponse<Object> saveAndUpdateData(@RequestBody WidgetConfigurationsDO widgetConfigurationsDO) {
        Boolean b;
        int maxRetries = 3;
        int attempt = 0;
        boolean success = false;
        appInsights.trackTrace("saveAndUpdateData 传的数据是： " + widgetConfigurationsDO);
        while (attempt < maxRetries && !success) {
            try {
                b = widgetConfigurationsService.saveAndUpdateData(widgetConfigurationsDO);
                if (Boolean.TRUE.equals(b)) {
                    success = true;
                    return new BaseResponse<>().CreateSuccessResponse(widgetConfigurationsDO);
                } else {
                    attempt++;
                    appInsights.trackTrace("saveAndUpdateData " + widgetConfigurationsDO.getShopName() + " 保存失败 (b=" + b + ")，正在重试第 " + (attempt + 1) + " 次");
                }
            } catch (Exception e) {
                attempt++;
                appInsights.trackTrace("saveAndUpdateData " + widgetConfigurationsDO.getShopName() + " 保存异常，正在重试第 " + (attempt + 1) + " 次: " + e.getMessage());
            }
        }
        return new BaseResponse<>().CreateErrorResponse("保存失败，已重试3次仍未成功");
    }


    //供前端查询数据API
    @PostMapping("/getData")
    public BaseResponse<Object> getData(@RequestBody WidgetConfigurationsDO widgetConfigurationsDO) {
        WidgetConfigurationsDO data = widgetConfigurationsService.getData(widgetConfigurationsDO.getShopName());
        if (data != null) {
            return new BaseResponse<>().CreateSuccessResponse(data);
        }else {
            return new BaseResponse<>().CreateErrorResponse("查询失败");
        }

    }
}
