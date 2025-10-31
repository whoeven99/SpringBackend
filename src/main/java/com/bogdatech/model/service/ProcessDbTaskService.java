package com.bogdatech.model.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.bogdatech.Service.ITranslateTasksService;
import com.bogdatech.Service.ITranslatesService;
import com.bogdatech.Service.ITranslationCounterService;
import com.bogdatech.Service.ITranslationUsageService;
import com.bogdatech.entity.DO.*;
import com.bogdatech.entity.VO.RabbitMqTranslateVO;
import com.bogdatech.exception.ClientException;
import com.bogdatech.logic.RabbitMqTranslateService;
import com.bogdatech.logic.ShopifyService;
import com.bogdatech.logic.TencentEmailService;
import com.bogdatech.logic.redis.TranslationMonitorRedisService;
import com.bogdatech.logic.redis.TranslationParametersRedisService;
import com.bogdatech.utils.CharacterCountUtils;
import com.bogdatech.utils.JsoupUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import static com.bogdatech.constants.TranslateConstants.*;
import static com.bogdatech.logic.redis.TranslationParametersRedisService.generateProgressTranslationKey;
import static com.bogdatech.utils.CaseSensitiveUtils.*;
import static com.bogdatech.utils.JsonUtils.jsonToObject;
import static com.bogdatech.utils.JsonUtils.stringToJson;

@Service
public class ProcessDbTaskService {
    @Autowired
    private RabbitMqTranslateService rabbitMqTranslateService;
    @Autowired
    private ITranslationCounterService translationCounterService;
    @Autowired
    private ITranslatesService translatesService;
    @Autowired
    private ITranslateTasksService translateTasksService;
    @Autowired
    private ITranslationUsageService translationUsageService;
    @Autowired
    private TencentEmailService tencentEmailService;
    @Autowired
    private TranslationMonitorRedisService translationMonitorRedisService;
    @Autowired
    private JsoupUtils jsoupUtils;
    @Autowired
    private TranslationParametersRedisService translationParametersRedisService;
    @Autowired
    private ShopifyService shopifyService;

    /**
     * 重新实现邮件发送方法。 能否获取这条数据之前是否有其他项没完成，没完成的话继续完成；完成的话，走发送邮件的逻辑。
     * 判断是否需要发送邮件，如果是走邮件发送，如果不是,走用户翻译
     */
    public void runTask(TranslateTasksDO task) {
        String shopName = task.getShopName();
        RabbitMqTranslateVO rabbitMqTranslateVO = jsonToObject(task.getPayload(), RabbitMqTranslateVO.class);
        if (rabbitMqTranslateVO == null) {
            appInsights.trackTrace("ProcessDBTaskLog FatalException: " + shopName + " 解析失败 " + task.getPayload());
            //将taskId 改为10（暂定）
            translateTasksService.updateByTaskId(task.getTaskId(), 10);
            return;
        }

        try {
            // 将redis状态中改为2
            translationParametersRedisService.hsetTranslationStatus(generateProgressTranslationKey(shopName, rabbitMqTranslateVO.getSource(), rabbitMqTranslateVO.getTarget()), String.valueOf(2));
            translationParametersRedisService.hsetTranslatingString(generateProgressTranslationKey(shopName, rabbitMqTranslateVO.getSource(), rabbitMqTranslateVO.getTarget()), "Searching for content to translate…");

            // 普通翻译任务
            processTask(shopName, rabbitMqTranslateVO.getModeType(), rabbitMqTranslateVO.getSource(), rabbitMqTranslateVO.getTarget(),
                    rabbitMqTranslateVO.getAccessToken(), rabbitMqTranslateVO.getLanguagePack(), rabbitMqTranslateVO.getTranslationModel(),
                    rabbitMqTranslateVO.getGlossaryMap(), rabbitMqTranslateVO.getLimitChars(), rabbitMqTranslateVO.getShopifyData(),
                    rabbitMqTranslateVO.getHandleFlag(), rabbitMqTranslateVO.getIsCover(),
                    task, EMAIL_TRANSLATE.equals(rabbitMqTranslateVO.getCustomKey()));

            // 将用户task改为1
            translateTasksService.updateByTaskId(task.getTaskId(), 1);

            // 删除所有status为1的数据
            translateTasksService.deleteStatus1Data();
        } catch (ClientException e1) {
            appInsights.trackTrace("ProcessDBTaskLog " + shopName + " 到达字符限制： " + e1);
        } catch (Exception e) {
            appInsights.trackTrace("ProcessDBTaskLog " + shopName + " 处理消息失败 errors : " + e);
            appInsights.trackException(e);
        }
    }

    public void processTask(String shopName, String modelType, String source, String target,
                            String accessToken, String languagePack, String translationModel,
                            Map<String, Object> glossaryMap, Integer limitChars, String shopifyData,
                            Boolean handleFlag, Boolean isCover,
                            TranslateTasksDO task, boolean isTranslationAuto) {
        Instant start = Instant.now();
        TranslationCounterDO counterDO = translationCounterService.readCharsByShopName(shopName);
        int usedChars = counterDO.getUsedChars();

        // TODO 1.1 翻译前的校验 判断字符是否超限
        checkTokenLimit(shopName, usedChars);

        if (isTranslationAuto) {
            appInsights.trackTrace("ProcessDBTaskLog isTranslationAuto 用户 " + shopName);
//            vo.setCustomKey(null);
        }

        appInsights.trackTrace("ProcessDBTaskLog 用户 ： " + shopName + " " + modelType + " 模块开始翻译前 counter 1: " + usedChars);

        appInsights.trackTrace("ProcessDBTaskLog translateByModeType：" + modelType
                + " 用户 ： " + shopName
                + " targetCode ：" + target
                + " source : " + source);
        // 初始化计数器
        CharacterCountUtils counter = new CharacterCountUtils();
        counter.addChars(usedChars);

        try {
            // TODO 1.2 翻译前 获取数据，过滤数据
            Map<String, Set<TranslateTextDO>> stringSetMap = getFilteredTranslationData(
                    shopName, accessToken, modelType, shopifyData,
                    handleFlag, target, isCover, glossaryMap);

            appInsights.trackTrace("ProcessDBTaskLog after FILTER, start translateByType：" + modelType
                    + " shop ： " + shopName
                    + " targetCode ：" + target
                    + " source : " + source);

            // TODO 2.1 翻译准备开始，设置状态
            translationParametersRedisService.hsetTranslatingModule(generateProgressTranslationKey(
                    shopName, source, target), modelType);
            translatesService.updateTranslatesResourceType(shopName, target, source, modelType);
            translateTasksService.updateByTaskId(task.getTaskId(), 2);

            // TODO 2.2 开始翻译流程
            for (Map.Entry<String, Set<TranslateTextDO>> entry : stringSetMap.entrySet()) {
                rabbitMqTranslateService.translateData(
                        entry.getValue(),
                        shopName, source, target, limitChars,
                        accessToken, languagePack, modelType,
                        translationModel, glossaryMap,
                        counter, entry.getKey());
            }

            // TODO 3 翻译后，存数据，记录等逻辑，拆出来

            appInsights.trackTrace("ProcessDBTaskLog 用户 ： " + shopName + " " + modelType + " 模块开始翻译后 counter 2: " + counter.getTotalChars() + " 单模块翻译结束。  " );

            // 一些monitor
            if (counter.getTotalChars() - usedChars > 0 && Duration.between(start, Instant.now()).toSeconds() > 0) {
                translationMonitorRedisService.hsetModelCharsWithTime(shopName, modelType,
                        counter.getTotalChars() - usedChars,
                        String.valueOf(Duration.between(start, Instant.now()).toSeconds()));
            }
        } catch (ClientException e1) {
            appInsights.trackTrace("ProcessDBTaskLog " + shopName + " 到达字符限制： " + e1);
            //将用户所有task改为3
            rabbitMqTranslateService.updateTranslateTasksStatus(shopName);
            translateTasksService.updateByTaskId(task.getTaskId(), 3);
            //将用户翻译状态也改为3
            translatesService.update(new UpdateWrapper<TranslatesDO>().eq("shop_name", shopName).eq("status", 2).set("status", 3));
            if (isTranslationAuto) {
                translationUsageService.update(new LambdaUpdateWrapper<TranslationUsageDO>()
                        .eq(TranslationUsageDO::getShopName, task.getShopName())
                        .set(TranslationUsageDO::getStatus, 0)
                        .set(TranslationUsageDO::getRemainingCredits, 0)
                        .set(TranslationUsageDO::getConsumedTime, 0)
                        .set(TranslationUsageDO::getCreditCount, 0));
            }
        } catch (Exception e) {
            appInsights.trackException(e);
            appInsights.trackTrace("ProcessDBTaskLog " + shopName + " 处理消息失败 errors : " + e);
            translateTasksService.updateByTaskId(task.getTaskId(), 4);
        }
    }

    private void checkTokenLimit(String shopName, int usedChars) {
        Integer maxCharsByShopName = translationCounterService.getMaxCharsByShopName(shopName);

        // Record monitor
        translationMonitorRedisService.hsetUsedCharsOfShop(shopName, usedChars);
        translationMonitorRedisService.hsetRemainingCharsOfShop(shopName, maxCharsByShopName);

        // 如果字符超限，则直接返回字符超限
        if (usedChars >= maxCharsByShopName) {
            appInsights.trackTrace("ProcessDBTaskLog 字符超限 " + shopName + " 当前消耗token为: " + usedChars);
            //将用户所有task改为3
            rabbitMqTranslateService.updateTranslateTasksStatus(shopName);
            //将用户翻译状态也改为3
            translatesService.update(new UpdateWrapper<TranslatesDO>().eq("shop_name", shopName).eq("status", 2).set("status", 3));
            throw new ClientException("字符超限");
        }
    }

    private Map<String, Set<TranslateTextDO>> getFilteredTranslationData(
            String shopName, String accessToken, String modeType, String shopifyData,
            Boolean handleFlag, String target, Boolean isCover, Map<String, Object> glossaryMap) {
        //根据DB的请求语句获取对应shopify值
        String shopifyDataByDb = shopifyService.getShopifyData(shopName, accessToken, APIVERSION, shopifyData);
        if (shopifyDataByDb == null) {
            // TODO 这里应该就是FatalException了，出现这个状况的话很严重
            appInsights.trackTrace("clickTranslation " + shopName + " shopifyDataByDb is null" );
            return new HashMap<>();
        }

        Set<TranslateTextDO> needTranslatedData = jsoupUtils.translatedDataParse(
                stringToJson(shopifyDataByDb), shopName, isCover, target);
        if (needTranslatedData == null) {
            return new HashMap<>();
        }
        Set<TranslateTextDO> filterTranslateData = jsoupUtils.filterNeedTranslateSet(
                modeType, handleFlag, needTranslatedData, shopName, target);
        //将筛选好的数据分类
        Map<String, Set<TranslateTextDO>> stringSetMap = rabbitMqTranslateService.filterTranslateMap(
                RabbitMqTranslateService.initTranslateMap(), filterTranslateData, glossaryMap);
        //实现功能： 分析三种类型数据， 添加模块标识，开始翻译
        if (stringSetMap.isEmpty()) {
            return new HashMap<>();
        }
        return stringSetMap;
    }
}
