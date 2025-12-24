package com.bogda.api.entity.DO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("APG_Email")
public class APGEmailDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String fromSend;
    private String toSend;
    private String subject;
    private Boolean flag;
}
