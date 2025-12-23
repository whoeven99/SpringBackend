package com.bogda.api.entity.VO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PCUsersVO {
    private String shopName;
    private String accessToken;
    private Integer purchasePoints;
    private Integer usedPoints;
    private String email;
    private String phone;
    private String realAddress;
    private String ipAddress;
    private String userTag;
    private String firstName;
    private String lastName;
    private Timestamp uninstallTime;
    private Timestamp loginTime;
    private Integer isDeleted;
    private Timestamp createAt;
    private Timestamp updateAt;
}
