package com.bogda.service.entity.DO;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("CharsOrders")
public class CharsOrdersDO {
    private String id;
    private String shopName;
    private Double amount;
    private String name;
    private LocalDateTime createdAt;
    private String status;
    private String confirmationUrl;

}
