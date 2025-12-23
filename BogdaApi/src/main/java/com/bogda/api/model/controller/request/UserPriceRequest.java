package com.bogda.api.model.controller.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class UserPriceRequest {
    private String shopName;
    private String accessToken;
    private String subscriptionId;
    private LocalDateTime createAt;
}
