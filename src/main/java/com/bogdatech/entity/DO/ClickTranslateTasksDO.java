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
@TableName("Click_Translate_Tasks")
public class ClickTranslateTasksDO {
    @TableId(type = IdType.AUTO)
    private String taskId;
    private Integer status; // 0:未开始 1:进行中 2:已完成 3:失败
    private String source; // 原语言
    private String target; // 目标语言
    private boolean cover = false; // 是否覆盖
    private String translateSettings1; // 模型 前端定的参数
    private String translateSettings2; // 语言包，先不管
    private String translateSettings3; // 模块类型
    private String customKey; // 自定义key
    private String shopName;
    private Boolean handle = false;
    private Timestamp createdAt;
}
