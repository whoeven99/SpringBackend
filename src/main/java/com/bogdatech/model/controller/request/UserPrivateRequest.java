package com.bogdatech.model.controller.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class UserPrivateRequest {
    @NotBlank(message = "店铺名不能为空")
    private String shopName;
    private String model;
    private String secret;
    private Integer amount;
}
