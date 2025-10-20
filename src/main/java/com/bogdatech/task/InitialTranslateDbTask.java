package com.bogdatech.task;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bogdatech.Service.ITranslateTasksService;
import com.bogdatech.Service.ITranslatesService;
import com.bogdatech.entity.DO.*;
import com.bogdatech.logic.TencentEmailService;
import com.bogdatech.logic.redis.TranslationCounterRedisService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bogdatech.Service.ITranslationCounterService;
import com.bogdatech.Service.IUsersService;
import com.bogdatech.logic.RabbitMqTranslateService;
import com.bogdatech.logic.redis.InitialTranslateRedisService;
import com.bogdatech.mapper.InitialTranslateTasksMapper;
import com.bogdatech.model.controller.request.ShopifyRequest;
import com.bogdatech.model.controller.request.TranslateRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import static com.bogdatech.constants.TranslateConstants.API_VERSION_LAST;
import static com.bogdatech.logic.RabbitMqTranslateService.CLICK_EMAIL;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;
import static com.bogdatech.utils.JsonUtils.jsonToObject;
import static com.bogdatech.utils.ListUtils.convertALL;
import static com.bogdatech.utils.RedisKeyUtils.generateProcessKey;

@Component
@EnableScheduling
public class InitialTranslateDbTask {
    @Autowired
    private RabbitMqTranslateService rabbitMqTranslateService;
    @Autowired
    private InitialTranslateRedisService initialTranslateRedisService;
    @Autowired
    private InitialTranslateTasksMapper initialTranslateTasksMapper;
    @Autowired
    private IUsersService iUsersService;
    @Autowired
    private ITranslatesService iTranslatesService;
    @Autowired
    private ITranslateTasksService iTranslateTasksService;
    @Autowired
    private ITranslationCounterService iTranslationCounterService;
    @Autowired
    private TranslationCounterRedisService translationCounterRedisService;
    @Autowired
    private TencentEmailService tencentEmailService;

    /**
     * 恢复因重启或其他原因中断的手动翻译大任务的task
     */
    @PostConstruct
    public void init() {
        if (System.getenv("REDISCACHEHOSTNAME") == null) {
            return;
        }
        appInsights.trackTrace("processInitialTasksOfShop init");
        initialTranslateRedisService.setDelete(); // 删掉翻译中的所有shop
    }

    /**
     * 定时查询是否有用户翻译完成，然后发送邮件
     * 判断条件：dbtask的task都执行成功，并且initial task status = 1
     * 已发送邮件，翻译任务完成 把发送邮件的标识存在initialTask里
     * dbtask的任务被中断，已发送邮件，翻译任务完成 把发送邮件的标识存在initialTask里
     */
    @Scheduled(fixedRate = 30 * 1000)
    public void scanAndSendEmail() {
        // 获取initial表里面 status=1 isDelete = false 的数据
        List<InitialTranslateTasksDO> initialTranslateTasks = initialTranslateTasksMapper.selectList(
                new LambdaQueryWrapper<InitialTranslateTasksDO>()
                        .eq(InitialTranslateTasksDO::getStatus, 1)
                        .eq(InitialTranslateTasksDO::isDeleted, false)
                        .eq(InitialTranslateTasksDO::isSendEmail, false));
        for (InitialTranslateTasksDO task : initialTranslateTasks) {
            // 获取Translates表里面 status的值。 2  和  3，  2做完成的判断， 3做部分翻译的状态
            TranslatesDO translatesDO = iTranslatesService.getSingleTranslateDO(task.getShopName(), task.getSource(), task.getTarget());
            if (translatesDO.getStatus().equals(7)){
                //手动暂停的的邮件不发送
                initialTranslateTasksMapper.update(new LambdaUpdateWrapper<InitialTranslateTasksDO>().eq(InitialTranslateTasksDO::getShopName, task.getShopName()).eq(InitialTranslateTasksDO::getTaskType, CLICK_EMAIL).set(InitialTranslateTasksDO::isSendEmail, 1));
            }

            // 获取该用户accessToken
            UsersDO userDO = iUsersService.getUserByName(task.getShopName());

            // 获取该用户目前消耗额度值
            TranslationCounterDO translationCounterDO = iTranslationCounterService.getTranslationCounterByShopName(userDO.getShopName());

            // 获取该用户额度限制
            Integer limitChars = iTranslationCounterService.getMaxCharsByShopName(task.getShopName());

            // 获取该用户 target 的 所有token 的值
            Long costToken = translationCounterRedisService.getLanguageData(generateProcessKey(task.getShopName(), task.getTarget()));
            Timestamp createdAt = task.getCreatedAt();
            LocalDateTime localDateTime = createdAt.toLocalDateTime();

            // 判断status的值
            if (translatesDO.getStatus() == 2) {
                // 判断该用户task是否全部完成
                List<TranslateTasksDO> translateTasks = iTranslateTasksService.getTranslateTasksByShopNameAndSourceAndTarget(task.getShopName(), task.getSource(), task.getTarget());
                System.out.println("translateTasks: " + translateTasks);

                // 先按原逻辑
                if (translateTasks.isEmpty() && !task.isSendEmail()){
                    // 8分钟后， 发送邮件
                    // 修改语言状态为1
                    iTranslatesService.updateTranslateStatus(task.getShopName(), 1, task.getTarget(), task.getSource());
                    rabbitMqTranslateService.triggerSendEmailLater(task.getShopName(), task.getTarget(), task.getSource(), userDO.getAccessToken(), localDateTime, costToken, translationCounterDO.getUsedChars(), limitChars);
                }
            } else if (translatesDO.getStatus() == 3 && !task.isSendEmail()) {
                // 为3，发送部分翻译的邮件
                // 将List<String> 转化位 List<TranslateResourceDTO>
                List<String> translationList = jsonToObject(task.getTranslateSettings3(), new TypeReference<List<String>>() {});
                if (translationList == null || translationList.isEmpty()){
                    appInsights.trackTrace("scanAndSendEmail 每日须看 translationList: " + translationList);
                    return;
                }
                List<TranslateResourceDTO> convertAll = convertALL(translationList);
                tencentEmailService.translateFailEmail(task.getShopName(), localDateTime, convertAll, task.getTarget(), task.getSource(), costToken);
                initialTranslateTasksMapper.update(new LambdaUpdateWrapper<InitialTranslateTasksDO>().eq(InitialTranslateTasksDO::getTaskId, task.getTaskId()).set(InitialTranslateTasksDO::isSendEmail, 1));
            }
        }
    }


    @Scheduled(fixedRate = 30 * 1000)
    public void scanAndSubmitInitialTranslateDbTask() {
        // 获取数据库中的翻译参数
        // 统计待翻译的 task
        List<InitialTranslateTasksDO> clickTranslateTasks = initialTranslateTasksMapper.selectList(new LambdaQueryWrapper<InitialTranslateTasksDO>().eq(InitialTranslateTasksDO::getStatus, 0).orderByAsc(InitialTranslateTasksDO::getCreatedAt));

        appInsights.trackTrace("scanAndSubmitInitialTranslateDbTask Number of clickTranslateTasks need to translate " + clickTranslateTasks.size());
        if (clickTranslateTasks.isEmpty()) {
            return;
        }

        // 遍历clickTranslateTasks，生成initialTasks
        for (InitialTranslateTasksDO task : clickTranslateTasks) {
            processInitialTasksOfShop(task);
        }
    }

    private void processInitialTasksOfShop(InitialTranslateTasksDO singleTask) {
        String shop = singleTask.getShopName();
        // 获取用户的accessToken
        UsersDO userDO = iUsersService.getOne(new LambdaQueryWrapper<UsersDO>().eq(UsersDO::getShopName, singleTask.getShopName()));
        appInsights.trackTrace("processInitialTasksOfShop task START: " + singleTask.getTaskId() + " of shop: " + shop);
        initialTranslateTasksMapper.update(new LambdaUpdateWrapper<InitialTranslateTasksDO>().eq(InitialTranslateTasksDO::getTaskId, singleTask.getTaskId()).set(InitialTranslateTasksDO::getStatus, 2));

        // 转化模块类型
        List<String> modelList = jsonToObject(singleTask.getTranslateSettings3(), new TypeReference<List<String>>() {
        });

        // taskType为 click 是手动翻译邮件， auto 是自动翻译邮件 ， key 是私有key邮件（这个暂时未实现）
        rabbitMqTranslateService.initialTasks(new ShopifyRequest(shop, userDO.getAccessToken(), API_VERSION_LAST, singleTask.getTarget()), modelList, new TranslateRequest(0, shop, userDO.getAccessToken(), singleTask.getSource(), singleTask.getTarget(), null), singleTask.isHandle(), singleTask.getTranslateSettings1(), singleTask.isCover(), singleTask.getCustomKey(), singleTask.getTaskType());
        appInsights.trackTrace("processInitialTasksOfShop task FINISH successfully: " + singleTask.getTaskId() + " of shop: " + shop);
        initialTranslateTasksMapper.update(new LambdaUpdateWrapper<InitialTranslateTasksDO>().eq(InitialTranslateTasksDO::getTaskId, singleTask.getTaskId()).set(InitialTranslateTasksDO::getStatus, 1));
    }
}
