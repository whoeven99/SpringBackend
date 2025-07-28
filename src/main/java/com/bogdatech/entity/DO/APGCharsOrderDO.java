package com.bogdatech.entity.DO;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("APG_chars_Order")
public class APGCharsOrderDO {
    private String id;
    private String shopName;
    private Double amount;
    private String name;
    private LocalDateTime createdAt;
    private String status;
    private String confirmationUrl;
}
