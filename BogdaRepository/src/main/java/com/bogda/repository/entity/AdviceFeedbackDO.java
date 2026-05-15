package com.bogda.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("advice_feedback")
public class AdviceFeedbackDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long adviceId;
    private String shopName;
    private String feedbackType;
    private Integer rating;
    private String notes;
    private LocalDateTime feedbackAt;
}
