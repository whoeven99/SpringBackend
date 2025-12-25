package com.bogda.common.model.controller.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class SignResponse implements Serializable {
    private String appKey;
    private String targetAppKey;
    private String signMethod;
    private Long timestamp;
    private String signStr;
}
