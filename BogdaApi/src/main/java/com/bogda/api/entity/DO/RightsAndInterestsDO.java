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
@TableName("RightsAndInterests")
public class RightsAndInterestsDO {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String rightName;
    private String rightDescribe;
    private String rightType;
    private String rightText;

}
