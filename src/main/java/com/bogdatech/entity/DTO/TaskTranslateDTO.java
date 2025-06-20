package com.bogdatech.entity.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class TaskTranslateDTO implements Serializable {
    private Integer status;
    private String shopName;
    private String accessToken;
    private String source; //原语言
    private String target; //目标语言
}
