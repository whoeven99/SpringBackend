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
    @Autowired
    private ITranslatesService iTranslatesService;
    @Autowired
    private ITranslationCounterService iTranslationCounterService;
    @Autowired
    private TranslationCounterRedisService translationCounterRedisService;
    @Autowired
    private InitialTranslateTasksMapper initialTranslateTasksMapper;
    @Autowired
    private TencentEmailService tencentEmailService;


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
        translationParametersRedisService.addWritingData(generateWriteStatusKey(cloudInsertRequest.getShopName(), cloudInsertRequest.getTarget()), WRITE_DONE, 1L);

        // 删除对应任务id
        userTranslationDataService.removeById(data.getTaskId());

        // 先获取当前进度条数据, 如果是翻译中,不管; 如果是写入,进行下面逻辑
        // 获取正在翻译的语言
        TranslatesDO translatesDO = iTranslatesService.getOne(new LambdaUpdateWrapper<TranslatesDO>().eq(TranslatesDO::getShopName, cloudInsertRequest.getShopName()).eq(TranslatesDO::getStatus, 2).eq(TranslatesDO::getTarget, cloudInsertRequest.getTarget()));

        Map<Object, Object> progressTranslationKey = translationParametersRedisService.getProgressTranslationKey(generateProgressTranslationKey(translatesDO.getShopName(), translatesDO.getSource(), translatesDO.getTarget()));
        String status = progressTranslationKey.get(TRANSLATION_STATUS).toString();
        if ("3".equals(status)) {
            // 判断是否该用户是否写入完成
            // 获取写入进度条数据,判断,如果写入的大于总共的, 修改翻译进度为1,修改进度条进度状态为4(写入完成)
            Map<String, Integer> writingData = translationParametersRedisService.getWritingData(cloudInsertRequest.getShopName(), cloudInsertRequest.getTarget());
            if (writingData != null && writingData.get(WRITE_DONE) != null && writingData.get(WRITE_TOTAL) != null && writingData.get(WRITE_DONE) >= writingData.get(WRITE_TOTAL)) {
                // 修改翻译进度为1
                boolean updateFlag = iTranslatesService.updateTranslateStatus(translatesDO.getShopName(), 1, translatesDO.getTarget(), translatesDO.getSource()) > 0;
                if (!updateFlag) {
                    appInsights.trackTrace("FatalException translationDataToSave 修改翻译进度失败 shopName : " + cloudInsertRequest.getShopName() + " target : " + cloudInsertRequest.getTarget());
                    return;
                }

                // 获取该用户 target 的 所有token 的值
                Long costToken = translationCounterRedisService.getLanguageData(generateProcessKey(translatesDO.getShopName(), translatesDO.getTarget()));

                // 获取initial task 的创建时间
                InitialTranslateTasksDO initialTranslateTasksDO = initialTranslateTasksMapper.selectOne(new QueryWrapper<InitialTranslateTasksDO>().select("TOP 1 id").eq("status", 1).eq("deleted", false).eq("send_email", false).eq("shop_name", translatesDO.getShopName()).eq("target", translatesDO.getTarget()).eq("source", translatesDO.getSource()));

                if (initialTranslateTasksDO == null) {
                    appInsights.trackTrace("FatalException translationDataToSave 获取initial task 失败 shopName : " + cloudInsertRequest.getShopName() + " target : " + translatesDO.getTarget() + " source : " + translatesDO.getSource());
                    return;
                }

                Timestamp createdAt = initialTranslateTasksDO.getCreatedAt();
                LocalDateTime localDateTime = createdAt.toLocalDateTime();

                // 获取该用户目前消耗额度值
                TranslationCounterDO translationCounterDO = iTranslationCounterService.getTranslationCounterByShopName(translatesDO.getShopName());

                // 获取该用户额度限制
                Integer limitChars = iTranslationCounterService.getMaxCharsByShopName(translatesDO.getShopName());

                triggerSendEmailLater(translatesDO.getShopName(), translatesDO.getTarget(), translatesDO.getSource(), translatesDO.getAccessToken(), localDateTime, costToken, translationCounterDO.getUsedChars(), limitChars, initialTranslateTasksDO.getTaskType());

                // 修改进度条进度状态为4(写入完成)
                translationParametersRedisService.hsetTranslationStatus(generateProgressTranslationKey(translatesDO.getShopName(), translatesDO.getSource(), translatesDO.getTarget()), String.valueOf(4));
                translationParametersRedisService.hsetTranslatingString(generateProgressTranslationKey(translatesDO.getShopName(), translatesDO.getSource(), translatesDO.getTarget()), "");
            }
        }
    }

    /**
     * 发送翻译成功的邮件
     */
    public void triggerSendEmailLater(String shopName, String target, String source, String accessToken, LocalDateTime startTime, Long costToken, Integer usedChars, Integer limitChars, String taskType) {
        System.out.println("clickTranslation " + shopName + " 异步发送邮件: " + LocalDateTime.now());
        tencentEmailService.translateSuccessEmail(new TranslateRequest(0, shopName, accessToken, source, target, null), startTime, costToken, usedChars, limitChars);
        System.out.println("clickTranslation 用户 " + shopName + " 翻译结束 时间为： " + LocalDateTime.now());
        initialTranslateTasksMapper.update(new LambdaUpdateWrapper<InitialTranslateTasksDO>().eq(InitialTranslateTasksDO::getShopName, shopName).eq(InitialTranslateTasksDO::getTaskType, taskType).set(InitialTranslateTasksDO::isSendEmail, 1));
    }
}
