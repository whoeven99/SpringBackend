package com.bogda.repository.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("User_Private_Translate")
public class UserPrivateTranslateDO extends BaseDO {
    private Integer apiName;    //API 名称, 0-google, 1-openai, 2-deepl, 3-deepSeek
    private Boolean apiStatus;  // api 状态 true-启用, false-禁用
//    private String promptWord;  // 用户自定义提示词 （目前暂时用不到）
//    private String apiModel;    // 用户设置模型类型 （目前暂时用不到，改为在点击翻译的时候，选择模型）
    private Long tokenLimit;    // 用户设置token限制
    private Long usedToken;     // 用户使用token
//    private Boolean isSelected; // 用户是否选择该api （目前暂时用不到）
    private String shopName;    // 用户商店名
    private String apiKey;      // 用户的api key
}
