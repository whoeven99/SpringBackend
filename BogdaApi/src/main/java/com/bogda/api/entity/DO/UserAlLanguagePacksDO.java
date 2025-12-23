package com.bogda.api.entity.DO;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "User_AILanguagePacks")
public class UserAlLanguagePacksDO {
    private String shopName;
    private String languagePack;
    private Integer packId;

}
