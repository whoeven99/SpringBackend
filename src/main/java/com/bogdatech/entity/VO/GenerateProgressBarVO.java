package com.bogdatech.entity.VO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GenerateProgressBarVO {

    private Long id;
    private Long userId;
    private Integer taskStatus;
    private String taskModel;
    private String taskData;
    private Integer allCount; //总数量
    private Integer unfinishedCount;//未完成数量
    private String productTitle; //产品标题
}
