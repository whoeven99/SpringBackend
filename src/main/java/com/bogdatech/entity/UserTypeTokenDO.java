package com.bogdatech.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("UserTypeToken")
public class UserTypeTokenDO {
    private int id;
    private int translationId;
    private int collection;
    private int notifications;
    private int theme;
    private int article;
    private int blogTitles;
    private int filters;
    private int metaobjects;
    private int pages;
    private int products;
    private int navigation;
    private int shop;
    private int shipping;
    private int delivery;
}
