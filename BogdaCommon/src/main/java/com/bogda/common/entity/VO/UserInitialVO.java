package com.bogda.common.entity.VO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserInitialVO {
    private String shopName;
    private String accessToken;
    private String email;
    private String userTag;
    private String firstName;
    private String lastName;
    private String defaultThemeName; // 主题名称
    private String defaultThemeId; // 主题id
    private String defaultLanguageData; // 默认语言
}
