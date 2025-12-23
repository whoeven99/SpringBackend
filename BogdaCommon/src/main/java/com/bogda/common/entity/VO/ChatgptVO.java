package com.bogda.common.entity.VO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatgptVO {
    private String content;
    private Integer allToken;
    private Integer promptToken;
    private Integer completionToken;
}
