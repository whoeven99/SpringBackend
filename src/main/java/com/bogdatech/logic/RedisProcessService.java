package com.bogdatech.logic;

import com.bogdatech.integration.RedisIntegration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static com.bogdatech.utils.RedisKeyUtils.PROGRESS_DONE;
import static com.bogdatech.utils.RedisKeyUtils.PROGRESS_TOTAL;

@Service
public class RedisProcessService {

    @Autowired
    private RedisIntegration redisIntegration;

    /**
     * redis添加进度条指定数据
     * */
    public void addProcessData(String key, String field , Long value){
        if (key == null) {
            return;
        }
         redisIntegration.incrementHash(key,field,value);
    }

    /**
     * redis获取进度条相关数据
     * */
    public String getFieldProcessData(String key, String field){
        return redisIntegration.getHash(key,field);
    }

    /**
     * 初始化对应数据
     * */
    public void initProcessData(String key) {
        redisIntegration.setHash(key, PROGRESS_TOTAL, 0);
        redisIntegration.setHash(key, PROGRESS_DONE, 0);
    }
    /**
     * 设置缓存时间为2周
     * 数据格式为 tr:{shopName}:{digest}
     * */
    public void setProcessData(String key, String field, String value){

    }
}
