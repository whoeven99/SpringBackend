package com.bogdatech.entity.DO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.sql.Timestamp;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("Initial_Translate_Tasks")
public class InitialTranslateTasksDO {
    @TableId(type = IdType.AUTO)
    private String taskId;
    private Integer status; // 0:未开始 1:task创建完成 2:进行中 3.翻译结束写入中 4.写入完成，翻译结束并且发完邮件了
    private String source; // 原语言
    private String target; // 目标语言
    private boolean cover = false; // 是否覆盖
    @TableField("send_email")
    private boolean sendEmail = false; // 是否发送邮件
    private String translateSettings1; // 模型 前端定的参数
    private String translateSettings2; // 语言包，先不管
    private String translateSettings3; // 模块类型
    private String customKey; // 自定义key
    private String shopName; // 店铺名称
    private boolean handle = false; // 是否翻译handle
    private String taskType; // 邮件标识
    private Timestamp createdAt; // task创建时间
    private boolean deleted = false; // 是否删除
}
