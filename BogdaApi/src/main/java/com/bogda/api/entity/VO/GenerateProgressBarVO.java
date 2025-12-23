package com.bogda.api.entity.VO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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
    private Integer status;//三个状态，初始化，提交，写入（1,2,3）
    private LocalDateTime taskTime; //该用户最近任务时间


}
