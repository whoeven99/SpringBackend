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
@TableName("User_Liquid")
public class UserLiquidDO {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String liquidId;
    private String shopName;
    private String liquidBeforeTranslation;
    private String liquidAfterTranslation;
    private String languageCode;
    private boolean isDeleted = false;
    private Timestamp createdAt;
    private Timestamp updatedAt;
}
