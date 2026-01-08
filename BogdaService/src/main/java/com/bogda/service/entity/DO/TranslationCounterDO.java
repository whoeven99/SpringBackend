package com.bogda.service.entity.DO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("TranslationCounter")
public class TranslationCounterDO {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String shopName;
    private Integer totalChars;
    private Integer chars;
    private Integer googleChars;
    private Integer openAiChars;
    private Integer usedChars;
}
