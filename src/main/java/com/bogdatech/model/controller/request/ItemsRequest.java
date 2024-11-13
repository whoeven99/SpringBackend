package com.bogdatech.model.controller.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ItemsRequest {
    private String itemName;
    private String target;
    private String shopName;
    private int translatedNumber;
    private int totalNumber;
}
