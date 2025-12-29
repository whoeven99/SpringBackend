package com.bogda.api.model.controller.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PurchaseSuccessRequest {
    private String shopName;
    private double amount;
    private double credit;
}
