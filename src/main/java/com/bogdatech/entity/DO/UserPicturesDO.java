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
@TableName("UserPictures")
public class UserPicturesDO {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String shopName;
    private String imageId;
    private String imageBeforeUrl;
    private String imageAfterUrl;
    private String altBeforeTranslation;
    private String altAfterTranslation;
    private String languageCode;
    private Boolean isDelete;
}
