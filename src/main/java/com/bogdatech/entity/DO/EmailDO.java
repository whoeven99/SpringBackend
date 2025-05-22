package com.bogdatech.entity.DO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EmailDO {
    private Integer id;
    private String shopName;
    private String fromSend;
    private String toSend;
    private String subject;
    private Integer flag;
}
