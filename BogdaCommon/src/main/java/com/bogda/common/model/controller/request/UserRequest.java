package com.bogda.common.model.controller.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserRequest {

    private String shopName;
    private String accessToken;
    private String email;
    private String phone;
    private String realAddress;
    private String ipAddress;
    private String userTag;

}
