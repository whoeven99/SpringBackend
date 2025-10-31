package com.bogdatech.logic;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bogdatech.Service.ITranslatesService;
import com.bogdatech.Service.ITranslationCounterService;
import com.bogdatech.Service.IUserTranslationDataService;
import com.bogdatech.entity.DO.*;
import com.bogdatech.logic.redis.TranslationCounterRedisService;
import com.bogdatech.logic.redis.TranslationParametersRedisService;
import com.bogdatech.mapper.InitialTranslateTasksMapper;
import com.bogdatech.model.controller.request.CloudInsertRequest;
import com.bogdatech.model.controller.request.TranslateRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.bogdatech.logic.ShopifyService.saveToShopify;
import static com.bogdatech.logic.redis.TranslationParametersRedisService.*;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.JsonUtils.jsonToObject;
import static com.bogdatech.utils.RedisKeyUtils.generateProcessKey;

@Service
public class UserTranslationDataService {
    @Autowired
    private IUserTranslationDataService userTranslationDataService;
    @Autowired
    private TranslationParametersRedisService translationParametersRedisService;


    /**
     * 将翻译后的文本以String的类型存储到数据库中
     */
    public Boolean insertTranslationData(String translationData, String shopName) {
        return userTranslationDataService.insertTranslationData(translationData, shopName);
    }

    /**
     * 读取用户翻译数据，暂定8个不同的用户（shopName）
     */
    public List<UserTranslationDataDO> selectTranslationDataList() {
        return userTranslationDataService.selectTranslationDataList();
    }

    public boolean updateStatusTo2(String taskId, int status) {
        return userTranslationDataService.update(new LambdaUpdateWrapper<UserTranslationDataDO>().eq(UserTranslationDataDO::getTaskId, taskId).set(UserTranslationDataDO::getStatus, status));
    }

    /**
     * 异步去做存shopify的处理
     */
    public void translationDataToSave(UserTranslationDataDO data) {
        String payload = data.getPayload();

        // 将payload解析
        CloudInsertRequest cloudInsertRequest;
        try {
            cloudInsertRequest = jsonToObject(payload, CloudInsertRequest.class);
        } catch (Exception e) {
            appInsights.trackTrace("translationDataToSave 存储失败 errors : " + e.getMessage());
            appInsights.trackException(e);
            return;
        }
        if (cloudInsertRequest == null) {
            return;
        }
        saveToShopify(cloudInsertRequest);
        translationParametersRedisService.addWritingData(generateWriteStatusKey(
                cloudInsertRequest.getShopName(), cloudInsertRequest.getTarget()), WRITE_DONE, 1L);

        // 删除对应任务id
        userTranslationDataService.removeById(data.getTaskId());
    }
}
