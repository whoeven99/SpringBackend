package com.bogdatech.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TranslatesDO {
    private int id;
    private String source;
    private String target;
    private String shopName;
    private int status;
    private Timestamp createAt;
    private Timestamp updateAt;
}
