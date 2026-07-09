package com.bogda.common.entity.VO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserInitialVO {
    private String shopName;
    private String accessToken;
    private String email;
    private String userTag;
    private String firstName;
    private String lastName;
}
