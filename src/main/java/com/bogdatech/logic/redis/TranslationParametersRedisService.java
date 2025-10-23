package com.bogdatech.logic.redis;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.bogdatech.Service.ITranslatesService;
import com.bogdatech.Service.ITranslationCounterService;
import com.bogdatech.entity.DO.InitialTranslateTasksDO;
import com.bogdatech.entity.DO.TranslatesDO;
import com.bogdatech.entity.DO.TranslationCounterDO;
import com.bogdatech.integration.RedisIntegration;
import com.bogdatech.logic.RedisProcessService;
import com.bogdatech.logic.TencentEmailService;
import com.bogdatech.mapper.InitialTranslateTasksMapper;
import com.bogdatech.model.controller.request.TranslateRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.RedisKeyUtils.*;

@Component
public class TranslationParametersRedisService {
    @Autowired
    private RedisIntegration redisIntegration;
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
    @Autowired
    private RedisProcessService redisProcessService;

    // 存储shop的停止标识
    private static final String STOP_TRANSLATION_KEY = "stop_translation_key_";

    // 存储shop的翻译进度条参数  hash存
    private static final String PROGRESS_TRANSLATION_KEY = "pt:{shopName}:{source}:{target}";

    // 存储shop邮件发送标识
    private static final String SEND_EMAIL_KEY = "send_email_key";

    public static final String TRANSLATING_MODULE = "translating_module";
    public static final String TRANSLATING_STRING = "translating_string";
    public static final String TRANSLATION_STATUS = "translation_status";

    // 写入状态
    private static final String PROGRESS_WRITE_KEY = "pw:{shopName}:{target}";
    public static final String WRITE_DONE = "write_done";
    public static final String WRITE_TOTAL = "write_total";

    /**
     * 生成写入状态的key
     */
    public static String generateWriteStatusKey(String shopName, String target) {
        if (shopName == null || target == null) {
            return null;
        }
        return PROGRESS_WRITE_KEY.replace("{shopName}", shopName)
                .replace("{target}", target);
    }

    /**
     * 删除写入key
     */
    public void delWritingDataKey(String shopName, String target) {
        redisIntegration.delete(generateWriteStatusKey(shopName, target));
    }

    /**
     * 存写入状态的数据 done 和 total
     */
    public Long addWritingData(String pwKey, String model, long data) {
        return redisIntegration.incrementHash(pwKey, model, data);
    }

    /**
     * 获取写入状态数据
     */
    public Map<String, Integer> getWritingData(String shopName, String target) {
        Map<Object, Object> hashAll = redisIntegration.getHashAll(generateWriteStatusKey(shopName, target));
        if (!CollectionUtils.isEmpty(hashAll)) {
            return hashAll.entrySet().stream().collect(Collectors.toMap(
                    e -> String.valueOf(e.getKey()),
                    e -> Integer.parseInt(e.getValue().toString())));
        }
        return new HashMap<>();
    }

    /**
     * 生成进度条key的翻译
     */
    public static String generateProgressTranslationKey(String shopName, String source, String target) {
        if (shopName == null || source == null || target == null) {
            return null;
        }
        return PROGRESS_TRANSLATION_KEY
                .replace("{shopName}", shopName)
                .replace("{source}", source)
                .replace("{target}", target);
    }

    /**
     * 存正在翻译的模块
     */
    public void hsetTranslatingModule(String ptKey, String module) {
        redisIntegration.setHash(ptKey, TRANSLATING_MODULE, module);
    }

    /**
     * 正在翻译字符串
     */
    public void hsetTranslatingString(String ptKey, String translatingString) {
        redisIntegration.setHash(ptKey, TRANSLATING_STRING, translatingString);
    }

    /**
     * 翻译状态：1-初始化 2-翻译中 3-写入中
     */
    public void hsetTranslationStatus(String ptKey, String status) {
        redisIntegration.setHash(ptKey, TRANSLATION_STATUS, status);
    }

    public Map<String, String> hgetAll(String ptKey) {
        return redisIntegration.hGetAll(ptKey);
    }

    /**
     * 获取shop，进度条相关数据
     */
    public Map<Object, Object> getProgressTranslationKey(String ptKey) {
        return redisIntegration.getHashAll(ptKey);
    }

    /**
     * 存进度条数字的key：tr:{shopName}:{targetCode}
     */
    public void hsetProgressNumber(String ptKey, String progressNumberKey) {
        redisIntegration.setHash(ptKey, "progress_number", progressNumberKey);
    }

    /**
     * 存用户的停止标识，如果返回ture，存成功； 如果返回false，存失败
     */
    public Boolean setStopTranslationKey(String shopName) {
        redisIntegration.set(STOP_TRANSLATION_KEY + shopName, "1");
        return true;
    }

    /**
     * 删除用户的停止标识
     */
    public Boolean delStopTranslationKey(String shopName) {
        return redisIntegration.delete(STOP_TRANSLATION_KEY + shopName);
    }

    /**
     * 判断获取到的停止标识是否是 “1”
     */
    public Boolean isStopped(String shopName) {
        return "1".equals(redisIntegration.get(STOP_TRANSLATION_KEY + shopName));
    }

    /**
     * 判断是否可以修改状态和发送邮件
     */
    public void translatedStatusAndSendEmail(String shopName, String target, String source) {
        // 获取进度条数据
        String total = redisProcessService.getFieldProcessData(generateProcessKey(shopName, target), PROGRESS_TOTAL);
        String done = redisProcessService.getFieldProcessData(generateProcessKey(shopName, target), PROGRESS_DONE);
        appInsights.trackTrace("translationDataToSave total: " + total + " done: " + done + " shopName: " + shopName + " target: " + target + " source: " + source);
        if (total == null || done == null || "null".equals(total) || "null".equals(done)) {
            return;
        }
        int totalNum = Integer.parseInt(total);
        int doneNum = Integer.parseInt(done);

        if (doneNum < totalNum) {
            return;
        }

        Map<Object, Object> progressTranslationKey = getProgressTranslationKey(generateProgressTranslationKey(shopName, source, target));
        String status = progressTranslationKey.get(TRANSLATION_STATUS).toString();
        appInsights.trackTrace("translationDataToSave status: " + status + " shopName: " + shopName + " target: " + target + " source: " + source);

        // 判断是否该用户是否写入完成
        // 获取写入进度条数据,判断,如果写入的大于总共的, 修改翻译进度为1,修改进度条进度状态为4(写入完成)
        Map<String, Integer> writingData = getWritingData(shopName, target);
        appInsights.trackTrace("translationDataToSave writingData: " + writingData + " shopName: " + shopName + " target: " + target + " source: " + source);
        appInsights.trackTrace("translationDataToSave done: " + writingData.get(WRITE_DONE) + " total: " + writingData.get(WRITE_TOTAL) + " shopName: " + shopName + " target: " + target + " source: " + source);
        if (writingData != null && writingData.get(WRITE_DONE) != null && writingData.get(WRITE_TOTAL) != null && writingData.get(WRITE_DONE) >= writingData.get(WRITE_TOTAL)) {
            // 修改翻译进度为1
            boolean updateFlag = iTranslatesService.updateTranslateStatus(shopName, 1, target, source) > 0;
            if (!updateFlag) {
                appInsights.trackTrace("FatalException translationDataToSave 修改翻译进度失败 shopName : " + shopName + " target : " + target);
                return;
            }

            // 获取该用户 target 的 所有token 的值
            Long costToken = translationCounterRedisService.getLanguageData(generateProcessKey(shopName, target));

            // 获取initial task 的创建时间
            InitialTranslateTasksDO initialTranslateTasksDO = initialTranslateTasksMapper.selectOne(new QueryWrapper<InitialTranslateTasksDO>().select("TOP 1 task_id").eq("status", 1).eq("deleted", false).eq("send_email", false).eq("shop_name", shopName).eq("target", target).eq("source", source));

            if (initialTranslateTasksDO == null) {
                appInsights.trackTrace("FatalException translationDataToSave 获取initial task 失败 shopName : " + shopName + " target : " + target + " source : " + source);
                return;
            }

            Timestamp createdAt = initialTranslateTasksDO.getCreatedAt();
            LocalDateTime localDateTime = createdAt.toLocalDateTime();

            // 获取该用户目前消耗额度值
            TranslationCounterDO translationCounterDO = iTranslationCounterService.getTranslationCounterByShopName(shopName);

            // 获取该用户额度限制
            Integer limitChars = iTranslationCounterService.getMaxCharsByShopName(shopName);

            triggerSendEmailLater(shopName, target, source, localDateTime, costToken, translationCounterDO.getUsedChars(), limitChars, initialTranslateTasksDO.getTaskType());

            // 修改进度条进度状态为4(写入完成)
            hsetTranslationStatus(generateProgressTranslationKey(shopName, source, target), String.valueOf(4));
            hsetTranslatingString(generateProgressTranslationKey(shopName, source, target), "");

        }
    }

    /**
     * 发送翻译成功的邮件
     */
    public void triggerSendEmailLater(String shopName, String target, String source, LocalDateTime startTime, Long costToken, Integer usedChars, Integer limitChars, String taskType) {
        System.out.println("clickTranslation " + shopName + " 异步发送邮件: " + LocalDateTime.now());
        tencentEmailService.translateSuccessEmail(new TranslateRequest(0, shopName, null, source, target, null), startTime, costToken, usedChars, limitChars);
        System.out.println("clickTranslation 用户 " + shopName + " 翻译结束 时间为： " + LocalDateTime.now());
        initialTranslateTasksMapper.update(new LambdaUpdateWrapper<InitialTranslateTasksDO>().eq(InitialTranslateTasksDO::getShopName, shopName).eq(InitialTranslateTasksDO::getTaskType, taskType).set(InitialTranslateTasksDO::isSendEmail, 1));
    }

}
