package com.bogda.service.entity.DO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("User_Private_Translate")
public class UserPrivateTranslateDO {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private Integer apiName;    //API 名称, 0-google, 1-openai, 2-deepl, 3-deepSeek
    private Boolean apiStatus;
    private String promptWord;
    private String apiModel;
    private Long tokenLimit;
    private Long usedToken;
    private Boolean isSelected;
    private String shopName;
    private String apiKey;
}
