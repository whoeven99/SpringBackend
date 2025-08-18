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
import com.bogdatech.logic.TencentEmailService;
import com.bogdatech.model.controller.request.TranslateRequest;
import com.bogdatech.utils.CharacterCountUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.bogdatech.constants.TranslateConstants.*;
import static com.bogdatech.logic.TranslateService.userTranslate;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.MapUtils.getTranslationStatusMap;

@Service
public class RabbitMqTranslateConsumerService {
    private final RabbitMqTranslateService rabbitMqTranslateService;
    private final ITranslationCounterService translationCounterService;
    private final ITranslatesService translatesService;
    private final ITranslateTasksService translateTasksService;
    private final ITranslationUsageService translationUsageService;
    private final TencentEmailService tencentEmailService;


    @Autowired
    public RabbitMqTranslateConsumerService(RabbitMqTranslateService rabbitMqTranslateService, ITranslationCounterService translationCounterService, ITranslatesService translatesService, ITranslateTasksService translateTasksService, ITranslationUsageService translationUsageService, TencentEmailService tencentEmailService) {
        this.rabbitMqTranslateService = rabbitMqTranslateService;
        this.translationCounterService = translationCounterService;
        this.translatesService = translatesService;
        this.translateTasksService = translateTasksService;
        this.translationUsageService = translationUsageService;
        this.tencentEmailService = tencentEmailService;
    }


    /**
     * 重新实现邮件发送方法。 能否获取这条数据之前是否有其他项没完成，没完成的话继续完成；完成的话，走发送邮件的逻辑。
     * 判断是否需要发送邮件，如果是走邮件发送，如果不是,走用户翻译
     */
    public void startTranslate(RabbitMqTranslateVO rabbitMqTranslateVO, TranslateTasksDO task) {
        String shopName = rabbitMqTranslateVO.getShopName();
        String shopifyData = rabbitMqTranslateVO.getShopifyData();
        boolean isEmail = EMAIL.equals(shopifyData);
        boolean isEmailAuto = EMAIL_AUTO.equals(shopifyData);
        boolean isTranslationAuto = EMAIL_TRANSLATE.equals(rabbitMqTranslateVO.getCustomKey());
        try {
            if (isEmail || isEmailAuto) {
                handleEmailTask(shopifyData, rabbitMqTranslateVO, task);
            } else {
                dbTranslate(rabbitMqTranslateVO, task, isTranslationAuto);
            }
            //删除所有status为1的数据
            translateTasksService.deleteStatus1Data();
        } catch (ClientException e1) {
            appInsights.trackTrace(shopName + " 到达字符限制： " + e1);
        } catch (Exception e) {
            appInsights.trackTrace(shopName + " 处理消息失败 errors : " + e);
            appInsights.trackException(e);
        }

    }


    // 消息处理方法
    public void processMessage(RabbitMqTranslateVO rabbitMqTranslateVO, TranslateTasksDO task, boolean isTranslationAuto) {
        //获取现在的时间，后面做减法
        Instant start = Instant.now();
        //判断字符是否超限
        TranslationCounterDO request1 = translationCounterService.readCharsByShopName(rabbitMqTranslateVO.getShopName());
        Integer remainingChars = rabbitMqTranslateVO.getLimitChars();
        int usedChars = request1.getUsedChars();
        //初始化计数器
        CharacterCountUtils counter = new CharacterCountUtils();
        counter.addChars(usedChars);
        // 如果字符超限，则直接返回字符超限
        if (usedChars >= remainingChars) {
            appInsights.trackTrace(rabbitMqTranslateVO.getShopName() + "字符超限 processMessage errors ");
            //将用户所有task改为3
            rabbitMqTranslateService.updateTranslateTasksStatus(rabbitMqTranslateVO.getShopName());
            //将用户翻译状态也改为3
            translatesService.update(new UpdateWrapper<TranslatesDO>().eq("shop_name", rabbitMqTranslateVO.getShopName()).eq("status", 2).set("status", 3));
            throw new ClientException("字符超限");
        }
        // 修改数据库当前翻译模块的数据
        translatesService.updateTranslatesResourceType(rabbitMqTranslateVO.getShopName(), rabbitMqTranslateVO.getTarget(), rabbitMqTranslateVO.getSource(), rabbitMqTranslateVO.getModeType());
        // 修改数据库的模块翻译状态
        translateTasksService.updateByTaskId(task.getTaskId(), 2);
        appInsights.trackTrace("用户 ： " + rabbitMqTranslateVO.getShopName() + " " + rabbitMqTranslateVO.getModeType() + " 模块开始翻译前 counter 1: " + counter.getTotalChars());
        try {
            if (isTranslationAuto) {
                rabbitMqTranslateVO.setCustomKey(null);
            }
            rabbitMqTranslateService.translateByModeType(rabbitMqTranslateVO, counter);
            appInsights.trackTrace("用户 ： " + rabbitMqTranslateVO.getShopName() + " " + rabbitMqTranslateVO.getModeType() + " 模块开始翻译后 counter 2: " + counter.getTotalChars() + " 单模块翻译结束。");
        } catch (ClientException e1) {
            appInsights.trackTrace(rabbitMqTranslateVO.getShopName() + "到达字符限制： " + e1);
            //将用户所有task改为3
            rabbitMqTranslateService.updateTranslateTasksStatus(rabbitMqTranslateVO.getShopName());
            translateTasksService.updateByTaskId(task.getTaskId(), 3);
            //将用户翻译状态也改为3
            translatesService.update(new UpdateWrapper<TranslatesDO>().eq("shop_name", rabbitMqTranslateVO.getShopName()).eq("status", 2).set("status", 3));
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
            appInsights.trackTrace(rabbitMqTranslateVO.getShopName() + " 处理消息失败 errors : " + e);
            translateTasksService.updateByTaskId(task.getTaskId(), 4);
        } finally {
            statisticalAutomaticTranslationData(isTranslationAuto, counter, usedChars, start, rabbitMqTranslateVO);
        }

    }

    /**
     * 判断是否是自动翻译任务，然后记录相关数据
     * */
    public void statisticalAutomaticTranslationData(Boolean isTranslationAuto, CharacterCountUtils counter, int usedChars, Instant start, RabbitMqTranslateVO rabbitMqTranslateVO){
        if (isTranslationAuto) {
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
    }

    /**
     * DB翻译
     */
    public void dbTranslate(RabbitMqTranslateVO rabbitMqTranslateVO, TranslateTasksDO task, boolean isTranslationAuto) {
        // 处理翻译功能
        processMessage(rabbitMqTranslateVO, task, isTranslationAuto);
        //将用户task改为1
        translateTasksService.updateByTaskId(task.getTaskId(), 1);

    }

    /**
     * EMAIL的邮件发送
     */
    public void emailTranslate(RabbitMqTranslateVO rabbitMqTranslateVO, TranslateTasksDO task) {
        try {
            Map<String, Object> translationStatusMap = getTranslationStatusMap(null, 3);
            userTranslate.put(rabbitMqTranslateVO.getShopName(), translationStatusMap);
            //将email的status改为2
            translateTasksService.removeById(task.getTaskId());
            //判断email类型，选择使用那个进行发送邮件
            rabbitMqTranslateService.sendTranslateEmail(rabbitMqTranslateVO, task, rabbitMqTranslateVO.getTranslateList());
            rabbitMqTranslateService.countAfterTranslated(new TranslateRequest(0, rabbitMqTranslateVO.getShopName(), rabbitMqTranslateVO.getAccessToken(), rabbitMqTranslateVO.getSource(), rabbitMqTranslateVO.getTarget(), null));
        } catch (Exception e) {
            appInsights.trackTrace("邮件发送 errors : " + e);
            appInsights.trackException(e);
        }
    }

    /**
     * EMAIL_AUTO的邮件发送
     */
    public void emailAutoTranslate(RabbitMqTranslateVO rabbitMqTranslateVO, TranslateTasksDO task) {
        try {
            String shopName = rabbitMqTranslateVO.getShopName();
            //判断数据库里面是否存在该语言
            //使用自动翻译的发送邮件逻辑
            //将status改为2
            translateTasksService.updateByTaskId(task.getTaskId(), 2);
            boolean update = translationUsageService.update(new LambdaUpdateWrapper<TranslationUsageDO>()
                    .eq(TranslationUsageDO::getShopName, shopName)
                    .eq(TranslationUsageDO::getLanguageName, rabbitMqTranslateVO.getTarget())
                    .set(TranslationUsageDO::getStatus, 1));
            if (update) {
                translateTasksService.removeById(task.getTaskId());
            }

            //判断TranslationUsage里面的语言是否都翻译了，如果有就发送邮件；没有的话，就跳过
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
        } catch (Exception e) {
            appInsights.trackTrace("邮件发送 errors : " + e);
            appInsights.trackException(e);
        }
    }

    /**
     * 两种邮件的实现功能
     */
    private void handleEmailTask(String shopifyData, RabbitMqTranslateVO vo, TranslateTasksDO task) {
        String shopName = vo.getShopName();
        boolean canSendEmail = translateTasksService.listBeforeEmailTask(shopName, task.getTaskId());
//        appInsights.trackTrace("canSendEmail: " + canSendEmail);
        if (canSendEmail) {
            if (EMAIL.equals(shopifyData)) {
                emailTranslate(vo, task);
            } else if (EMAIL_AUTO.equals(shopifyData)) {
                emailAutoTranslate(vo, task);
            }

            //将翻译项中的模块改为null
            translatesService.update(new LambdaUpdateWrapper<TranslatesDO>().eq(TranslatesDO::getShopName, shopName)
                    .eq(TranslatesDO::getSource, vo.getSource())
                    .eq(TranslatesDO::getTarget, vo.getTarget())
                    .set(TranslatesDO::getResourceType, null));
        } else {
            appInsights.trackTrace(shopName + " 还有数据没有翻译完: " + task.getTaskId() + "，继续翻译");
//            appInsights.trackTrace(shopName + " 还有数据没有翻译完: " + task.getTaskId() + "，继续翻译");
        }
    }
}
