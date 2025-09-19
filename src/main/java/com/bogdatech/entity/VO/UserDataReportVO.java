package com.bogdatech.entity.VO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class UserDataReportVO {
    private String shopName;
    private String[] storeLanguage; //页面语言
    private String eventName; //事件名
    private String pageEventId; //事件id
    private Timestamp timestamp; //时间戳
    private String clientId; // 客户id
    private int dayData; //获取几天的数据
}
