package com.bogda.service.entity.VO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class IncludeCrawlerVO {
    private String uaInformation;
    private String uaReason;
}
