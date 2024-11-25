package com.bogdatech.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("Translates")
public class TranslatesDO {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String source;
    private String accessToken;
    private String target;
    private String shopName;
    private Integer status;
//    private Timestamp createAt;
//    private Timestamp updateAt;
}
