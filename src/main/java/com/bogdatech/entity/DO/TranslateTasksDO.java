package com.bogdatech.entity.DO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("TranslateTasks")
public class TranslateTasksDO {
    @TableId(type = IdType.AUTO)
    private String taskId;
    private Integer status;
    private String payload;
    private String shopName;
    private Integer allTasks;
    private Timestamp createdAt;
}
