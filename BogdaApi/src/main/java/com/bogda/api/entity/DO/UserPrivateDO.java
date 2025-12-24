package com.bogda.api.entity.DO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@TableName("UserPrivate")
public class UserPrivateDO {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String shopName;
    private Integer amount;
    private Integer usedAmount;
    private String openaiKey;
    private String googleKey;

}
