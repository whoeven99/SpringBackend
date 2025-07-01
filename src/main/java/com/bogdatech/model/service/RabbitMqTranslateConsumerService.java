package com.bogdatech.model.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.bogdatech.Service.ITranslateTasksService;
import com.bogdatech.Service.ITranslatesService;
import com.bogdatech.Service.ITranslationCounterService;
import com.bogdatech.entity.DO.TranslateTasksDO;
import com.bogdatech.entity.DO.TranslatesDO;
import com.bogdatech.entity.DO.TranslationCounterDO;
import com.bogdatech.entity.VO.RabbitMqTranslateVO;
import com.bogdatech.exception.ClientException;
import com.bogdatech.logic.RabbitMqTranslateService;
import com.bogdatech.utils.CharacterCountUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.bogdatech.constants.TranslateConstants.EMAIL;
import static com.bogdatech.logic.TranslateService.userTranslate;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.MapUtils.getTranslationStatusMap;

@Service
public class RabbitMqTranslateConsumerService {
    private final RabbitMqTranslateService rabbitMqTranslateService;
    private final ITranslationCounterService translationCounterService;
    private final ITranslatesService translatesService;
    private final ITranslateTasksService translateTasksService;
    private final TaskScheduler taskScheduler;

    @Autowired
    public RabbitMqTranslateConsumerService(RabbitMqTranslateService rabbitMqTranslateService, ITranslationCounterService translationCounterService, ITranslatesService translatesService, ITranslateTasksService translateTasksService, TaskScheduler taskScheduler) {
        this.rabbitMqTranslateService = rabbitMqTranslateService;
        this.translationCounterService = translationCounterService;
        this.translatesService = translatesService;
        this.translateTasksService = translateTasksService;
        this.taskScheduler = taskScheduler;
    }


    // 初始化时调用一次，启动全局队列监听器
    // 启动监听器用于处理该用户的消息
    public void startTranslate(RabbitMqTranslateVO rabbitMqTranslateVO, TranslateTasksDO task) {
        try {
            if (EMAIL.equals(rabbitMqTranslateVO.getShopifyData())) {
                // 判断是否需要发送邮件，获取TranslateTasks的所有关于该商店的task，判断是否只剩一条数据了，如果是走邮件发送，如果不是不发邮件
                List<TranslateTasksDO> shopName = translateTasksService.list(new QueryWrapper<TranslateTasksDO>().eq("shop_name", rabbitMqTranslateVO.getShopName()).and(wrapper -> wrapper.eq("status", 2).or().eq("status", 0)));
                if (shopName.size() <= 1) {
                    appInsights.trackTrace(rabbitMqTranslateVO.getShopName() + " 只剩一条数据了，发送邮件");
                    //获取当前用户翻译状态，先不做
                    // 处理邮件发送功能
                    try {
                        appInsights.trackTrace("date1: " + LocalDateTime.now());
                        //将用户状态改为3，
                        Map<String, Object> translationStatusMap = getTranslationStatusMap(null, 3);
                        userTranslate.put(rabbitMqTranslateVO.getShopName(), translationStatusMap);
                        triggerSendEmailLater(rabbitMqTranslateVO, task, rabbitMqTranslateVO.getTranslateList());
                    } catch (Exception e) {
                        appInsights.trackTrace("邮件发送 errors : " + e);
                    }
                }else {
                    appInsights.trackTrace(rabbitMqTranslateVO.getShopName() + " 还有数据继续翻译");
                }
            } else {
                // 处理翻译功能
                try {
                    processMessage(rabbitMqTranslateVO, task);
                    //将用户task改为1
                    translateTasksService.updateByTaskId(task.getTaskId(), 1);
                } catch (ClientException e1) {
                    appInsights.trackTrace(rabbitMqTranslateVO.getShopName() + "到达字符限制： " + e1);
                    //将用户所有task改为3
                    translateTasksService.updateByTaskId(task.getTaskId(), 3);
                    //将用户翻译状态也改为3
                    translatesService.update(new UpdateWrapper<TranslatesDO>().eq("shop_name", rabbitMqTranslateVO.getShopName()).eq("status", 2).set("status", 3));
                } catch (Exception e) {
                    appInsights.trackTrace(rabbitMqTranslateVO.getShopName() + "处理消息失败 errors : " + e);
                    translateTasksService.updateByTaskId(task.getTaskId(), 4);
                }
            }
            //删除所有status为1的数据
            translateTasksService.deleteStatus1Data();
        } catch (ClientException e1) {
            appInsights.trackTrace(rabbitMqTranslateVO.getShopName() + "到达字符限制： " + e1);
        } catch (Exception e) {
            appInsights.trackTrace(rabbitMqTranslateVO.getShopName() + "处理消息失败 errors : " + e);
        }
    }


    // 模拟消息处理函数
    public void processMessage(RabbitMqTranslateVO rabbitMqTranslateVO, TranslateTasksDO task) {
        // 这里可以放真正的翻译处理逻辑
        //判断字符是否超限
        TranslationCounterDO request1 = translationCounterService.readCharsByShopName(rabbitMqTranslateVO.getShopName());
        Integer remainingChars = rabbitMqTranslateVO.getLimitChars();
        int usedChars = request1.getUsedChars();
        // 如果字符超限，则直接返回字符超限
        if (usedChars >= remainingChars) {
            appInsights.trackTrace(rabbitMqTranslateVO.getShopName() + "字符超限 processMessage errors ");
            //将用户所有task改为3
            translateTasksService.updateByTaskId(task.getTaskId(), 3);
            throw new ClientException("字符超限");
        }
        // 修改数据库当前翻译模块的数据
        translatesService.updateTranslatesResourceType(rabbitMqTranslateVO.getShopName(), rabbitMqTranslateVO.getTarget(), rabbitMqTranslateVO.getSource(), rabbitMqTranslateVO.getModeType());
        // 修改数据库的模块翻译状态
        translateTasksService.updateByTaskId(task.getTaskId(), 2);
        rabbitMqTranslateVO.setLimitChars(remainingChars);
        //初始化计数器
        CharacterCountUtils counter = new CharacterCountUtils();
        counter.addChars(usedChars);
        appInsights.trackTrace("用户 ： " + rabbitMqTranslateVO.getShopName() + " " + rabbitMqTranslateVO.getModeType() + " 模块开始翻译前 counter 1: " + counter.getTotalChars());
        rabbitMqTranslateService.translateByModeType(rabbitMqTranslateVO, counter);
        appInsights.trackTrace("用户 ： " + rabbitMqTranslateVO.getShopName() + " " + rabbitMqTranslateVO.getModeType() + " 模块开始翻译后 counter 2: " + counter.getTotalChars());
        appInsights.trackTrace("用户 ： " + rabbitMqTranslateVO.getShopName() + " " + rabbitMqTranslateVO.getModeType() + " 单模块翻译结束。");

    }

    public void triggerSendEmailLater(RabbitMqTranslateVO rabbitMqTranslateVO, TranslateTasksDO task, List<String> translationList) {
        // 创建一个任务 Runnable
        Runnable delayedTask = () -> {
            appInsights.trackTrace("date2: " + LocalDateTime.now());
            rabbitMqTranslateService.sendTranslateEmail(rabbitMqTranslateVO, task, translationList);
        };

        // 设置执行时间为当前时间 + 10分钟（使用 Instant 代替 Date）
        Instant runAt = Instant.now().plusSeconds(15 * 60);

        // 使用推荐的 API
        taskScheduler.schedule(delayedTask, runAt);
    }
}
