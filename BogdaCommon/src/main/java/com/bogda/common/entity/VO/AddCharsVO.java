package com.bogda.common.entity.VO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AddCharsVO {
    private String shopName;
    private Integer chars;
    private String gid; // 计划id
    private String accessToken;
}
