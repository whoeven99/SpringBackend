package com.bogda.service.controller.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserSubscriptionsRequest {
    private String shopName;
    private int planId;
    private int status;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
}
