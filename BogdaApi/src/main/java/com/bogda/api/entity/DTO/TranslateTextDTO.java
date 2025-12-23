package com.bogda.api.entity.DTO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TranslateTextDTO {

    @TableId(value = "id", type = IdType.AUTO)
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

    @TableField(exist = false)
    private Boolean outdated;
}
