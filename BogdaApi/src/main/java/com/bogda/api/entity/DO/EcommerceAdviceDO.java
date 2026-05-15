package com.bogda.api.entity.DO;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EcommerceAdviceDO {
    private Long id;
    private String shopName;
    private String adviceType;
    private String adviceContent;
    private String targetResourceId;
    private String targetResourceType;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
