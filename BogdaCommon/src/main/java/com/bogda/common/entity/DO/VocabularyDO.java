package com.bogda.common.entity.DO;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("Vocabulary")
public class VocabularyDO {
    @TableId(value = "vid", type = IdType.AUTO)
    private Long vid;
    private String en;
    private String es;
    private String fr;
    private String de;
    @TableField("pt_BR")
    private String ptBR;
    @TableField("pt_PT")
    private String ptPT;
    @TableField("zh_CN")
    private String zhCN;
    @TableField("zh_TW")
    private String zhTW;
    private String ja;
    private String it;
    private String ru;
    private String ko;
    private String nl;
    private String da;
    private String hi;
    private String bg;
    private String cs;
    private String el;
    private String fi;
    private String hr;
    private String hu;
    @TableField(value = "id",insertStrategy = FieldStrategy.IGNORED)
    private String id;
    @TableField(value = "lt",insertStrategy = FieldStrategy.IGNORED)
    private String lt;
    private String nb;
    private String pl;
    private String ro;
    private String sk;
    private String sl;
    private String sv;
    private String th;
    private String tr;
    private String vi;
    private String ar;
    private String no;
    private String uk;
    private String lv;
    private String et;
}
