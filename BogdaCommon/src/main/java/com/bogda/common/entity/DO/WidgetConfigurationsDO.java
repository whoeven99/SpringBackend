package com.bogda.common.entity.DO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@TableName("WidgetConfigurations")
public class WidgetConfigurationsDO {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String shopName;
    private Boolean languageSelector;
    private Boolean currencySelector;
    private Boolean ipOpen;
    private Boolean includedFlag;
    private String fontColor;
    private String backgroundColor;
    private String buttonColor;
    private String buttonBackgroundColor;
    private String optionBorderColor;
    private String selectorPosition;
    private String positionData;
    private Boolean isTransparent;
}
