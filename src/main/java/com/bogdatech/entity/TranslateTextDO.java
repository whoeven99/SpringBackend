package com.bogdatech.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("TranslateTextTable")
public class TranslateTextDO {
//    @TableId(type = IdType.AUTO)
    private Integer id;
    private String shopName;
    private String resourceId;
    private String textType;
    private String digest;
    private String textKey;
    private String sourceText;
    private String targetText;
    private String sourceCode;
    private String targetCode;

}
