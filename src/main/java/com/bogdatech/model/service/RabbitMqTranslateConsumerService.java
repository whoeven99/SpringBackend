package com.bogdatech.model.service;

import com.bogdatech.Service.ITranslateTasksService;
import com.bogdatech.Service.ITranslatesService;
import com.bogdatech.Service.ITranslationCounterService;
import com.bogdatech.entity.DO.TranslateTasksDO;
import com.bogdatech.entity.DO.TranslationCounterDO;
import com.bogdatech.entity.VO.RabbitMqTranslateVO;
import com.bogdatech.exception.ClientException;
import com.bogdatech.logic.RabbitMqTranslateService;
import com.bogdatech.utils.CharacterCountUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import static com.bogdatech.constants.TranslateConstants.EMAIL;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

@Service
public class RabbitMqTranslateConsumerService {
    private final RabbitMqTranslateService rabbitMqTranslateService;
    private final ITranslationCounterService translationCounterService;
    private final ITranslatesService translatesService;
    private final ITranslateTasksService translateTasksService;

    @Autowired
    public RabbitMqTranslateConsumerService(RabbitMqTranslateService rabbitMqTranslateService, ITranslationCounterService translationCounterService, ITranslatesService translatesService, ITranslateTasksService translateTasksService) {
        this.rabbitMqTranslateService = rabbitMqTranslateService;
        this.translationCounterService = translationCounterService;
        this.translatesService = translatesService;
        this.translateTasksService = translateTasksService;
    }


    // 初始化时调用一次，启动全局队列监听器
    // 启动监听器用于处理该用户的消息
    public void startTranslate(RabbitMqTranslateVO rabbitMqTranslateVO, TranslateTasksDO task) {
        try {
            if (EMAIL.equals(rabbitMqTranslateVO.getShopifyData())) {
                //获取当前用户翻译状态，先不做
                // 处理邮件发送功能
                try {
                    rabbitMqTranslateService.sendTranslateEmail(rabbitMqTranslateVO, task);
                } catch (Exception e) {
                    appInsights.trackTrace("邮件发送 errors : " + e);
                }
                translateTasksService.updateByTaskId(task.getTaskId(), 1);
            } else {
                // 处理翻译功能
                processMessage(rabbitMqTranslateVO, task);
            }
            //删除所有status为1的数据
            translateTasksService.deleteStatus1Data();
        } catch (ClientException e1) {
            appInsights.trackTrace("到达字符限制： " + e1);
        } catch (Exception e) {
            appInsights.trackTrace("处理消息失败 errors : " + e);
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
            appInsights.trackTrace("字符超限 processMessage errors ");
            return;
        }
        // 修改数据库当前翻译模块的数据
        translatesService.updateTranslatesResourceType(rabbitMqTranslateVO.getShopName(), rabbitMqTranslateVO.getTarget(), rabbitMqTranslateVO.getSource(), rabbitMqTranslateVO.getModeType());
        // 修改数据库的模块翻译状态
        translateTasksService.updateByTaskId(task.getTaskId(), 2);
        rabbitMqTranslateVO.setLimitChars(remainingChars);
        //初始化计数器
        CharacterCountUtils counter = new CharacterCountUtils();
        counter.addChars(usedChars);
        System.out.println("用户 ： " + rabbitMqTranslateVO.getShopName() + " 模块开始翻译前 counter 1: " + counter.getTotalChars());
        rabbitMqTranslateService.translateByModeType(rabbitMqTranslateVO, counter);
        System.out.println("用户 ： " + rabbitMqTranslateVO.getShopName() + " 模块开始翻译后 counter 2: " + counter.getTotalChars());
        System.out.println("用户 ： " + rabbitMqTranslateVO.getShopName() + " 单模块翻译结束。");
        //将用户task改为1
        System.out.println("task: " + task.getTaskId());
        translateTasksService.updateByTaskId(task.getTaskId(), 1);
    }


}
