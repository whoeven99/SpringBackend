package com.bogda.service.entity.DO;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("APG_Chars_Order")
public class APGCharsOrderDO {
    private String id;
    private Long userId;
    private Double amount;
    private String name;
    private LocalDateTime createdAt;
    private String status;
    private String confirmationUrl;
}
