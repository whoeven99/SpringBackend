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
import com.bogdatech.integration.RedisIntegration;
import com.bogdatech.logic.ShopifyService;
import com.bogdatech.logic.TencentEmailService;
import com.bogdatech.logic.redis.TranslationCounterRedisService;
import com.bogdatech.logic.redis.TranslationMonitorRedisService;
import com.bogdatech.logic.redis.TranslationParametersRedisService;
import com.bogdatech.utils.CharacterCountUtils;
import com.bogdatech.utils.JsoupUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static com.bogdatech.constants.TranslateConstants.*;
import static com.bogdatech.logic.redis.TranslationParametersRedisService.generateProgressTranslationKey;
import static com.bogdatech.utils.CaseSensitiveUtils.*;
import static com.bogdatech.utils.JsonUtils.jsonToObject;
import static com.bogdatech.utils.JsonUtils.stringToJson;
import static com.bogdatech.utils.RedisKeyUtils.generateProcessKey;

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
    private RedisIntegration redisIntegration;
    @Autowired
    private TranslationMonitorRedisService translationMonitorRedisService;
    @Autowired
    private JsoupUtils jsoupUtils;
    @Autowired
    private TranslationParametersRedisService translationParametersRedisService;
    @Autowired
    private TranslationCounterRedisService translationCounterRedisService;
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
        String source = rabbitMqTranslateVO.getSource();
        String target = rabbitMqTranslateVO.getTarget();

        String shopifyData = rabbitMqTranslateVO.getShopifyData();
        try {
            // TODO 等email都转到initialTasks后，这里可以删掉
            if (EMAIL.equals(shopifyData) || EMAIL_AUTO.equals(shopifyData)) {
                // email任务
                boolean canSendEmail = translateTasksService.listBeforeEmailTask(shopName, task.getTaskId());
                if (canSendEmail) {
                    translationParametersRedisService.hsetTranslationStatus(generateProgressTranslationKey(shopName, source, target), String.valueOf(3));
                    translationParametersRedisService.hsetTranslatingString(generateProgressTranslationKey(shopName, source, target), "");
                    if (EMAIL.equals(shopifyData)) {
                        translateTasksService.updateByTaskId(task.getTaskId(), 1);
                    } else {
                        emailAutoTranslate(rabbitMqTranslateVO, task);
                    }
                } else {
                    // TODO 这是个fatalException 出现这种问题都是比较严重的问题 没翻译完为什么会出现email的任务
                    appInsights.trackTrace(shopName + " 还有数据没有翻译完: " + task.getTaskId() + "，继续翻译");
                }
            } else {
                // 将redis状态中改为2
                translationParametersRedisService.hsetTranslationStatus(generateProgressTranslationKey(shopName, rabbitMqTranslateVO.getSource(), rabbitMqTranslateVO.getTarget()), String.valueOf(2));
                translationParametersRedisService.hsetTranslatingString(generateProgressTranslationKey(shopName, rabbitMqTranslateVO.getSource(), rabbitMqTranslateVO.getTarget()), "Searching for content to translate…");

                // 普通翻译任务
                processTask(rabbitMqTranslateVO, task, EMAIL_TRANSLATE.equals(rabbitMqTranslateVO.getCustomKey()));

                // 将用户task改为1
                translateTasksService.updateByTaskId(task.getTaskId(), 1);
            }
            // 删除所有status为1的数据
            translateTasksService.deleteStatus1Data();
        } catch (ClientException e1) {
            appInsights.trackTrace("ProcessDBTaskLog " + shopName + " 到达字符限制： " + e1);
        } catch (Exception e) {
            appInsights.trackTrace("ProcessDBTaskLog " + shopName + " 处理消息失败 errors : " + e);
            appInsights.trackException(e);
        }
    }

    public void processTask(RabbitMqTranslateVO vo, TranslateTasksDO task, boolean isTranslationAuto) {
        String shopName = vo.getShopName();
        Instant start = Instant.now();
        TranslationCounterDO counterDO = translationCounterService.readCharsByShopName(shopName);
        int usedChars = counterDO.getUsedChars();

        // TODO 1.1 翻译前的校验 判断字符是否超限
        checkTokenLimit(shopName, usedChars);

        if (isTranslationAuto) {
            appInsights.trackTrace("ProcessDBTaskLog isTranslationAuto 用户 " + shopName);
            vo.setCustomKey(null);
        }

        appInsights.trackTrace("ProcessDBTaskLog 用户 ： " + shopName + " " + vo.getModeType() + " 模块开始翻译前 counter 1: " + usedChars);

        appInsights.trackTrace("ProcessDBTaskLog translateByModeType：" + vo.getModeType()
                + " 用户 ： " + vo.getShopName()
                + " targetCode ：" + vo.getTarget()
                + " source : " + vo.getSource());
        // 初始化计数器
        CharacterCountUtils counter = new CharacterCountUtils();
        counter.addChars(usedChars);

        try {
            // TODO 1.2 翻译前 获取数据，过滤数据
            Map<String, Set<TranslateTextDO>> stringSetMap = getFilteredTranslationData(vo);

            appInsights.trackTrace("ProcessDBTaskLog after FILTER, start translateByType：" + vo.getModeType()
                    + " shop ： " + vo.getShopName()
                    + " targetCode ：" + vo.getTarget()
                    + " source : " + vo.getSource());

            // TODO 2.1 翻译准备开始，设置状态
            translationParametersRedisService.hsetTranslatingModule(generateProgressTranslationKey(
                    shopName, vo.getSource(), vo.getTarget()), vo.getModeType());
            translatesService.updateTranslatesResourceType(shopName, vo.getTarget(), vo.getSource(), vo.getModeType());
            translateTasksService.updateByTaskId(task.getTaskId(), 2);

            // TODO 2.2 开始翻译流程
            for (Map.Entry<String, Set<TranslateTextDO>> entry : stringSetMap.entrySet()) {
                rabbitMqTranslateService.translateData(entry.getValue(), vo, counter, entry.getKey());
            }

            // TODO 3 翻译后，存数据，记录等逻辑，拆出来

            appInsights.trackTrace("ProcessDBTaskLog 用户 ： " + shopName + " " + vo.getModeType() + " 模块开始翻译后 counter 2: " + counter.getTotalChars() + " 单模块翻译结束。  " );

            // 一些monitor
            if (counter.getTotalChars() - usedChars > 0 && Duration.between(start, Instant.now()).toSeconds() > 0) {
                translationMonitorRedisService.hsetModelCharsWithTime(shopName, vo.getModeType(),
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
        } finally {
            statisticalAutomaticTranslationData(isTranslationAuto, counter, usedChars, start, vo);
        }
    }

    private void checkTokenLimit(String shopName, int usedChars) {
        Integer maxCharsByShopName = translationCounterService.getMaxCharsByShopName(shopName);

        // Record monitor
        translationMonitorRedisService.hsetUsedCharsOfShop(shopName, usedChars);
        translationMonitorRedisService.hsetRemainingCharsOfShop(shopName, maxCharsByShopName);

        // 如果字符超限，则直接返回字符超限
        if (usedChars >= maxCharsByShopName) {
            appInsights.trackTrace("ProcessDBTaskLog 字符超限 " + shopName +" 当前消耗token为: " + usedChars);
            //将用户所有task改为3
            rabbitMqTranslateService.updateTranslateTasksStatus(shopName);
            //将用户翻译状态也改为3
            translatesService.update(new UpdateWrapper<TranslatesDO>().eq("shop_name", shopName).eq("status", 2).set("status", 3));
            throw new ClientException("字符超限");
        }
    }

    private Map<String, Set<TranslateTextDO>> getFilteredTranslationData(RabbitMqTranslateVO vo) {
        //根据DB的请求语句获取对应shopify值
        String shopifyDataByDb = shopifyService.getShopifyData(vo.getShopName(), vo.getAccessToken(), APIVERSION, vo.getShopifyData());
        if (shopifyDataByDb == null) {
            // TODO 这里应该就是FatalException了，出现这个状况的话很严重
            appInsights.trackTrace("clickTranslation " + vo.getShopName() + " shopifyDataByDb is null" + vo);
            return new HashMap<>();
        }

        Set<TranslateTextDO> needTranslatedData = jsoupUtils.translatedDataParse(
                stringToJson(shopifyDataByDb), vo.getShopName(), vo.getIsCover(),
                vo.getTarget());
        if (needTranslatedData == null) {
            return new HashMap<>();
        }
        Set<TranslateTextDO> filterTranslateData = jsoupUtils.filterNeedTranslateSet(
                vo.getModeType(), vo.getHandleFlag(), needTranslatedData,
                vo.getShopName(), vo.getTarget());
        //将筛选好的数据分类
        Map<String, Set<TranslateTextDO>> stringSetMap = rabbitMqTranslateService.filterTranslateMap(
                RabbitMqTranslateService.initTranslateMap(), filterTranslateData, vo.getGlossaryMap());
        //实现功能： 分析三种类型数据， 添加模块标识，开始翻译
        if (stringSetMap.isEmpty()) {
            return new HashMap<>();
        }
        return stringSetMap;
    }

    /**
     * 判断是否是自动翻译任务，然后记录相关数据
     */
    public void statisticalAutomaticTranslationData(Boolean isTranslationAuto, CharacterCountUtils counter, int usedChars, Instant start, RabbitMqTranslateVO rabbitMqTranslateVO) {
        if (!isTranslationAuto) {
            return;
        }
        // 获取消耗的token值
        int totalChars = counter.getTotalChars();
        int translatedChars = totalChars - usedChars;
        // 获取结束时间
        Instant end = Instant.now();
        // 计算耗时
        Duration duration = Duration.between(start, end);
        long seconds = duration.getSeconds();
        //先获取目前已消耗的字符数和剩余字数数
        TranslationCounterDO one = translationCounterService.getOne(new LambdaQueryWrapper<TranslationCounterDO>().eq(TranslationCounterDO::getShopName, rabbitMqTranslateVO.getShopName()));

        //修改自动翻译邮件数据，将消耗的字符数，剩余字符数
        TranslationUsageDO usageServiceOne = translationUsageService.getOne(new LambdaQueryWrapper<TranslationUsageDO>()
                .eq(TranslationUsageDO::getShopName, rabbitMqTranslateVO.getShopName())
                .eq(TranslationUsageDO::getLanguageName, rabbitMqTranslateVO.getTarget()));

        try {
            translationUsageService.update(new LambdaUpdateWrapper<TranslationUsageDO>()
                    .eq(TranslationUsageDO::getShopName, rabbitMqTranslateVO.getShopName())
                    .eq(TranslationUsageDO::getLanguageName, rabbitMqTranslateVO.getTarget())
                    .set(TranslationUsageDO::getConsumedTime, usageServiceOne.getConsumedTime() + seconds)
                    .set(TranslationUsageDO::getRemainingCredits, rabbitMqTranslateVO.getLimitChars() - one.getUsedChars())
                    .set(TranslationUsageDO::getCreditCount, usageServiceOne.getCreditCount() + translatedChars));
        } catch (Exception e) {
            appInsights.trackException(e);
        }
    }

    /**
     * EMAIL_AUTO的邮件发送
     */
    public void emailAutoTranslate(RabbitMqTranslateVO rabbitMqTranslateVO, TranslateTasksDO task) {
        try {
            String shopName = rabbitMqTranslateVO.getShopName();
            // 判断数据库里面是否存在该语言
            // 使用自动翻译的发送邮件逻辑
            // 将status改为2
            translateTasksService.updateByTaskId(task.getTaskId(), 2);
            boolean update = translationUsageService.update(new LambdaUpdateWrapper<TranslationUsageDO>()
                    .eq(TranslationUsageDO::getShopName, shopName)
                    .eq(TranslationUsageDO::getLanguageName, rabbitMqTranslateVO.getTarget())
                    .set(TranslationUsageDO::getStatus, 1));
            if (update) {
                translateTasksService.removeById(task.getTaskId());
            }

            // 判断TranslationUsage里面的语言是否都翻译了，如果有就发送邮件；没有的话，就跳过
            List<TranslatesDO> list = translatesService.list(new QueryWrapper<TranslatesDO>().eq("shop_name", task.getShopName()).eq("auto_translate", true));
            Boolean b = translationUsageService.judgeSendAutoEmail(list, rabbitMqTranslateVO.getShopName());
            if (b) {
                tencentEmailService.sendAutoTranslateEmail(shopName);
                //将所有status, remaining，consumed， credit都改为0
                translationUsageService.update(new LambdaUpdateWrapper<TranslationUsageDO>()
                        .eq(TranslationUsageDO::getShopName, task.getShopName())
                        .set(TranslationUsageDO::getStatus, 0)
                        .set(TranslationUsageDO::getRemainingCredits, 0)
                        .set(TranslationUsageDO::getConsumedTime, 0)
                        .set(TranslationUsageDO::getCreditCount, 0));
            }

            appInsights.trackTrace("ProcessDBTaskLog 用户 " + shopName + " 自动翻译结束 时间为： " + LocalDateTime.now());
            // 删除redis该用户相关进度条数据
            redisIntegration.delete(generateProcessKey(rabbitMqTranslateVO.getShopName(), rabbitMqTranslateVO.getTarget()));
        } catch (Exception e) {
            appInsights.trackTrace("ProcessDBTaskLog " + rabbitMqTranslateVO.getShopName() + " 邮件发送 errors : " + e);
            appInsights.trackException(e);
        }
    }
}
