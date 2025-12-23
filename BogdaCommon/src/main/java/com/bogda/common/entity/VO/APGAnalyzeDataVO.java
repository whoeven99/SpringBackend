package com.bogda.common.entity.VO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class APGAnalyzeDataVO {
    private double wordCount;
    private double wordGap;
    private String keywordStrong;
    private double keywordPercent;
    private double keywordCompare;
    private double textPercent;
    private double ctrIncrease;
    private String generateText;
}
