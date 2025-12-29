package com.bogda.api.entity.VO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class APGTokenVO {
    private Integer userToken;
    private Integer allToken;
}
