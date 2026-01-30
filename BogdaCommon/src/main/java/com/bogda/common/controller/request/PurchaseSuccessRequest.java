package com.bogda.common.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PurchaseSuccessRequest {
    @NotBlank(message = "店铺名不能为空")
    private String shopName;
    @Min(value = 0, message = "amount不能为负数")
    private double amount;
    @Min(value = 0, message = "credit不能为负数")
    private double credit;
}
