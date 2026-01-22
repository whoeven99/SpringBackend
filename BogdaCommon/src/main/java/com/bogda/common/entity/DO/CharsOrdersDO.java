package com.bogda.common.entity.DO;

import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("CharsOrders")
public class CharsOrdersDO {
    @NotBlank(message = "订单号不能为空")
    private String id;
    @NotBlank(message = "店铺名不能为空")
    private String shopName;
    private Double amount;
    private String name;
    private LocalDateTime createdAt;
    @NotBlank(message = "订单状态不能为空")
    private String status;
    private String confirmationUrl;

}
