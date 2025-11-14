package com.bogdatech.entity.DO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BaseDO {
    @TableId(type = IdType.AUTO)
    private Integer id;
    @TableField("is_deleted")
    private Boolean isDeleted;
    @TableField("created_at")
    private Timestamp createdAt;
    @TableField("updated_at")
    private Timestamp updatedAt;
}
