package com.bogda.api.entity.DO;

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
@TableName("PC_User_Pictures")
public class PCUserPicturesDO {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String shopName;
    private String imageId;
    private String productId;
    private String mediaId;
    private String imageBeforeUrl;
    private String imageAfterUrl;
    private String altBeforeTranslation;
    private String altAfterTranslation;
    private String languageCode;
    private Integer isDeleted;
    private Timestamp createAt;
    private Timestamp updateAt;
}
