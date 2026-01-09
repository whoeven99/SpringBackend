package com.bogda.service.entity.VO;

import com.bogda.repository.entity.UserIPRedirectionDO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WidgetReturnVO {
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
    private List<UserIPRedirectionDO> ipRedirections;

}
