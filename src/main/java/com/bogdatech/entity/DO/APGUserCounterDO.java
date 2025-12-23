package com.bogdatech.entity.DO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("APG_User_Counter")
public class APGUserCounterDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Integer chars;
    private Integer productCounter;
    private Integer productSeoCounter;
    private Integer collectionCounter;
    private Integer collectionSeoCounter;
    private Integer allCounter;
    private Integer extraCounter;
    private Integer userToken;
}
