package com.bogdatech.entity.DO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@AllArgsConstructor
@NoArgsConstructor
@Data
@TableName("Glossary")
public class GlossaryDO {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String shopName;
    private String sourceText;
    private String targetText;
    private String rangeCode;
    private Integer caseSensitive;
    private Integer status;
    private Timestamp createdDate;

    public GlossaryDO(String sourceText, String targetText,  Integer caseSensitive) {
        this.sourceText = sourceText;
        this.targetText = targetText;
        this.caseSensitive = caseSensitive;
    }
}
