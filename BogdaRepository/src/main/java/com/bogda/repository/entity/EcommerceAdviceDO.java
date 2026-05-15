package com.bogda.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ecommerce_advice")
public class EcommerceAdviceDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String shopName;
    private String adviceType;
    private String adviceContent;
    private String targetResourceId;
    private String targetResourceType;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
