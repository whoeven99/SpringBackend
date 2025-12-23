package com.bogda.api.entity.DO;

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
@TableName("APG_User_Generated_Subtask")
public class APGUserGeneratedSubtaskDO {
    @TableId(type = IdType.AUTO)
    private String subtaskId;
    private Integer status;
    private String payload;
    private Long userId;
    private Timestamp createTime;
}
