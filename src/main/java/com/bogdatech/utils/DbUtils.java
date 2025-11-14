package com.bogdatech.utils;

import com.bogdatech.entity.DO.BaseDO;
import java.sql.Timestamp;

public class DbUtils {
    public static void setUpdatedAt(BaseDO baseDO) {
        baseDO.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
    }

    public static void setAllTime(BaseDO baseDO) {
        long now = System.currentTimeMillis();
        baseDO.setUpdatedAt(new Timestamp(now));
        baseDO.setCreatedAt(new Timestamp(now));
    }
}
