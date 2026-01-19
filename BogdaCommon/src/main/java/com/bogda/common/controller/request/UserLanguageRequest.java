package com.bogda.common.controller.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class UserLanguageRequest {
    private String shopName;
    private Integer packId;
}
