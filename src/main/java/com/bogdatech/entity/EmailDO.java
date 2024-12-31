package com.bogdatech.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EmailDO {
    private String id;
    private String shopName;
    private String fromSend;
    private String toSend;
    private String subject;
    private Integer flag;
}
