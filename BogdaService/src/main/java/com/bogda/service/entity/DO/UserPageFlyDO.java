package com.bogda.service.entity.DO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("User_Page_Fly")
public class UserPageFlyDO {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String shopName;
    private String sourceText;
    private String targetText;
    private String languageCode;
    private boolean isDeleted = false;
    private Timestamp updatedAt;

    public boolean getIsDeleted() {
        return isDeleted;
    }
}
