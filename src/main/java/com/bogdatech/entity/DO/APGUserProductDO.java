package com.bogdatech.entity.DO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("APG_User_Product")
public class APGUserProductDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String productId;
    private Long userId;
    private Integer createVision;
    private Boolean isDelete;
    private String generateContent;
    private Timestamp updateTime;
    private String pageType; //pro col类型
    private String contentType; //title seo类型
}
