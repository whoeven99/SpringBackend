package com.bogda.common.entity.DO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("TranslationUsage")
public class TranslationUsageDO {
    @TableId(type = IdType.INPUT)
    private Integer translateId; //翻译项id
    private String shopName; //店铺名称
    private String languageName; //语言代码
    private Integer creditCount; //消耗token数
    private Integer consumedTime;//消耗的时间/分钟
    private Integer remainingCredits; //还剩下的token数
    private Integer status; //是否使用 0，未统计； 1，统计
}
