package com.bogda.api.entity.DO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("APG_User_Generated_Task")
public class APGUserGeneratedTaskDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Integer taskStatus;
    private String taskModel;
    private String taskData;
    private LocalDateTime updateTime;
}
