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
@TableName("Items")
public class ItemsDO {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String shopName;
    private String itemName;
    private Integer totalNumber;
    private String target;
    private Integer translatedNumber;
    private Integer status;

    public ItemsDO(String itemName, int totalNumber, int translatedNumber, String target, Integer status) {
        this.itemName = itemName;
        this.totalNumber = totalNumber;
        this.translatedNumber = translatedNumber;
        this.target = target;
        this.status = status;
    }
}
