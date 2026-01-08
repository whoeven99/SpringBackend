package com.bogda.service.entity.DO;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("UserTypeToken")
public class UserTypeTokenDO {
    private Integer  id;
    private Integer  translationId;
    private Integer  collection;
    private Integer  notifications;
    private Integer  theme;
    private Integer  article;
    private Integer  blogTitles;
    private Integer  filters;
    private Integer  metaobjects;
    private Integer  pages;
    private Integer  products;
    private Integer  navigation;
    private Integer  shop;
    private Integer  shipping;
    private Integer metadata;
    private String shopName;
}
