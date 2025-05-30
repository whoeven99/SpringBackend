package com.bogdatech.model.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.bogdatech.Service.ITranslatesService;
import com.bogdatech.Service.ITranslationCounterService;
import com.bogdatech.Service.ITranslationUsageService;
import com.bogdatech.Service.IUsersService;
import com.bogdatech.entity.DO.TranslatesDO;
import com.bogdatech.entity.DO.TranslationCounterDO;
import com.bogdatech.entity.DO.TranslationUsageDO;
import com.bogdatech.entity.DO.UsersDO;
import com.bogdatech.entity.DTO.TranslateDTO;
import com.bogdatech.logic.TencentEmailService;
import com.bogdatech.logic.TranslateService;
import com.bogdatech.model.controller.request.TranslateRequest;
import com.bogdatech.utils.CharacterCountUtils;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

import static com.bogdatech.constants.RabbitMQConstants.SCHEDULED_TRANSLATE_QUEUE;
import static com.bogdatech.utils.JsonUtils.jsonToObject;

@Service
public class TranslateTaskConsumerService {

    private final TranslateService translateService;
    private final ITranslatesService translatesService;
    private final ITranslationCounterService translationCounterService;
    private final ITranslationUsageService translationUsageService;
    private final TencentEmailService tencentEmailService;
    private final IUsersService usersService;

    @Autowired
    public TranslateTaskConsumerService(TranslateService translateService, ITranslatesService translatesService, ITranslationCounterService translationCounterService, ITranslationUsageService translationUsageService, TencentEmailService tencentEmailService, IUsersService usersService) {
        this.translateService = translateService;
        this.translatesService = translatesService;
        this.translationCounterService = translationCounterService;
        this.translationUsageService = translationUsageService;
        this.tencentEmailService = tencentEmailService;
        this.usersService = usersService;
    }

    /**
     * 接收翻译任务，并执行处理
     */
    @RabbitListener(queues = SCHEDULED_TRANSLATE_QUEUE)
    public void scheduledTranslateTask(String json, Channel channel, Message rawMessage) throws IOException {
        String deliveryTag = String.valueOf(rawMessage.getMessageProperties().getDeliveryTag());
        TranslateDTO translateDTO = jsonToObject(json, TranslateDTO.class);
        try {
            if (translateDTO == null) {
                channel.basicAck(rawMessage.getMessageProperties().getDeliveryTag(), false);
                return;
            }
            String shopName = translateDTO.getShopName();
            //判断该用户是否正在翻译，正在翻译就不翻译了
            List<Integer> integers = translatesService.readStatusInTranslatesByShopName(shopName);
            for (Integer integer : integers) {
                if (integer == 2) {
                    channel.basicNack(rawMessage.getMessageProperties().getDeliveryTag(), false, true);
                    return;
                }
            }

            TranslationCounterDO request1 = translationCounterService.readCharsByShopName(shopName);
            Integer remainingChars = translationCounterService.getMaxCharsByShopName(shopName);
            int usedChars = request1.getUsedChars();

            translatesService.updateTranslateStatus(translateDTO.getShopName(), 2, translateDTO.getTarget(), translateDTO.getSource(), translateDTO.getAccessToken());
            //从user表里面获取token
            UsersDO usersDO = usersService.getOne(new QueryWrapper<UsersDO>().eq("shop_name", shopName));
            //初始化计数器
            CharacterCountUtils counter = new CharacterCountUtils();
            counter.addChars(usedChars);
            //autoTranslateException，对异常进行处理
            TranslateRequest request = new TranslateRequest(0, translateDTO.getShopName(), usersDO.getAccessToken(), translateDTO.getSource(), translateDTO.getTarget(),null);
            translateService.autoTranslateException(request, remainingChars, counter, usedChars);
            // 业务处理完成后，手动ACK消息，RabbitMQ可安全移除消息
            channel.basicAck(rawMessage.getMessageProperties().getDeliveryTag(), false);
            //判断TranslationUsage里面的语言是否都翻译了，如果有就发送邮件；没有的话，就跳过
            List<TranslatesDO> list = translatesService.list(new QueryWrapper<TranslatesDO>().eq("shop_name", shopName).eq("auto_translate", true));
            Boolean b = translationUsageService.judgeSendAutoEmail(list, shopName);
            if (b) {
                tencentEmailService.sendAutoTranslateEmail(shopName);
                //将所有status都改为0
                translationUsageService.update(new UpdateWrapper<TranslationUsageDO>()
                        .eq("shop_name", shopName)
                        .set("status", 0));
            }
        } catch (Exception e) {
            // 可以选择不ack，丢回队列（false代表仅当前消息）
            channel.basicNack(rawMessage.getMessageProperties().getDeliveryTag(), false, true);
        }
    }
}
