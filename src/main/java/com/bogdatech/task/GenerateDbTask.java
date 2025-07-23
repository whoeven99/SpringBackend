package com.bogdatech.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bogdatech.Service.*;
import com.bogdatech.entity.DO.APGUserCounterDO;
import com.bogdatech.entity.DO.APGUserGeneratedSubtaskDO;
import com.bogdatech.entity.DO.APGUserGeneratedTaskDO;
import com.bogdatech.entity.DO.APGUsersDO;
import com.bogdatech.entity.VO.GenerateDescriptionVO;
import com.bogdatech.entity.VO.GenerateEmailVO;
import com.bogdatech.exception.ClientException;
import com.bogdatech.logic.GenerateDescriptionService;
import com.bogdatech.utils.CharacterCountUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.bogdatech.logic.TranslateService.OBJECT_MAPPER;
import static com.bogdatech.logic.TranslateService.executorService;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

@Service
public class GenerateDbTask {

    private final IAPGUserGeneratedSubtaskService iapgUserGeneratedSubtaskService;
    private final GenerateDescriptionService generateDescriptionService;
    private final IAPGUsersService iapgUsersService;
    private final IAPGUserPlanService iapgUserPlanService;
    private final IAPGUserCounterService iapgUserCounterService;
    private final IAPGUserGeneratedTaskService iapgUserGeneratedTaskService;
    private static final Set<Long> GENERATE_SHOP = ConcurrentHashMap.newKeySet(); //判断用户是否正在生成描述
    @Autowired
    public GenerateDbTask(IAPGUserGeneratedSubtaskService iapgUserGeneratedSubtaskService, GenerateDescriptionService generateDescriptionService, IAPGUsersService iapgUsersService, IAPGUserPlanService iapgUserPlanService, IAPGUserCounterService iapgUserCounterService, IAPGUserGeneratedTaskService iapgUserGeneratedTaskService) {
        this.iapgUserGeneratedSubtaskService = iapgUserGeneratedSubtaskService;
        this.generateDescriptionService = generateDescriptionService;
        this.iapgUsersService = iapgUsersService;
        this.iapgUserPlanService = iapgUserPlanService;
        this.iapgUserCounterService = iapgUserCounterService;
        this.iapgUserGeneratedTaskService = iapgUserGeneratedTaskService;
    }

    // 每1秒钟检查一次是否有闲置线程
    @Scheduled(fixedDelay = 3000)
    public void scanAndGenerateSubtask() {
        // 获取所有status为0的数据
        List<APGUserGeneratedSubtaskDO> list = iapgUserGeneratedSubtaskService.list(new LambdaQueryWrapper<APGUserGeneratedSubtaskDO>().eq(APGUserGeneratedSubtaskDO::getStatus, 0).orderBy(true, true, APGUserGeneratedSubtaskDO::getCreateTime));
        // 循环异步翻译
        for (APGUserGeneratedSubtaskDO subtaskDO : list
             ) {
            // 一个用户同一时间只能翻译一个
            System.out.println("用户 " + subtaskDO.getUserId() + " 开始遍历 子任务： " + subtaskDO.getSubtaskId());
            if (!GENERATE_SHOP.contains(subtaskDO.getUserId())) {
                GENERATE_SHOP.add(subtaskDO.getUserId());

                    executorService.submit(() -> {
                        System.out.println("用户 " + subtaskDO.getUserId() + " 开始生成 子任务： " + subtaskDO.getSubtaskId());
                        try {
                            // 做判断，是邮件还是生成任务
                            if (subtaskDO.getPayload().contains("\"email\":\"EMAIL\"")) {
                                // 调用发送邮件接口
                                sendDescriptionsEmail(subtaskDO);
                                return;
                            }
                            // 调用单条生成接口
                            fixGenerateSubtask(subtaskDO);
                        } catch (Exception e) {
                            System.err.println(e);
                            appInsights.trackTrace("用户 " + subtaskDO.getUserId() + " 生成 子任务： " + subtaskDO.getSubtaskId() + " errors ：" + e);
                        }
                    });
            }
        }

    }

    /**
     * 调用单条生成接口
     * */
    public void fixGenerateSubtask(APGUserGeneratedSubtaskDO subtaskDO) {
        // 修改子任务状态为2
        Boolean b = iapgUserGeneratedSubtaskService.updateStatusById(subtaskDO.getSubtaskId(), 2);
//        System.out.println("修改子任务状态为2是否正确：" + b);
        // 获取用户数据
        APGUsersDO usersDO = iapgUsersService.getOne(new LambdaQueryWrapper<APGUsersDO>().eq(APGUsersDO::getId, subtaskDO.getUserId()));
        // 获取用户最大额度
        Integer userMaxLimit = iapgUserPlanService.getUserMaxLimit(usersDO.getId());

        // 将String数据，处理成 GenerateDescriptionVO数据
        GenerateDescriptionVO gvo;
        CharacterCountUtils counter = new CharacterCountUtils();
        try {
            gvo = OBJECT_MAPPER.readValue(subtaskDO.getPayload(), GenerateDescriptionVO.class);
            generateDescriptionService.generateDescription(usersDO, gvo, counter, userMaxLimit);
        } catch (JsonProcessingException e) {
            appInsights.trackTrace(usersDO.getShopName() + " 用户 fixGenerateSubtask errors ：" + e);
            //将该任务状态改为4
            iapgUserGeneratedSubtaskService.updateStatusById(subtaskDO.getSubtaskId(), 4);
            System.out.println(usersDO.getShopName() + " 用户 fixGenerateSubtask errors ：" + e);
        } catch (ClientException e1){
            iapgUserGeneratedSubtaskService.updateStatusById(subtaskDO.getSubtaskId(), 3);
            iapgUserGeneratedTaskService.updateStatusByUserId(usersDO.getId(), 3);
            appInsights.trackTrace(usersDO.getShopName() + " 用户 fixGenerateSubtask errors ：" + e1);
            System.out.println(usersDO.getShopName() + " 用户 fixGenerateSubtask errors ：" + e1);
        } catch (Exception e2){
            iapgUserGeneratedSubtaskService.updateStatusById(subtaskDO.getSubtaskId(), 4);
            appInsights.trackTrace(usersDO.getShopName() + " 用户 fixGenerateSubtask errors ：" + e2);
            System.out.println(usersDO.getShopName() + " 用户 fixGenerateSubtask errors ：" + e2);
        }finally {
            //删除状态为2的子任务
            APGUserGeneratedSubtaskDO gs = iapgUserGeneratedSubtaskService.getById(subtaskDO.getSubtaskId());
            if (gs.getStatus() == 2) {
                iapgUserGeneratedSubtaskService.removeById(subtaskDO.getSubtaskId());
            }
            //删除限制
            GENERATE_SHOP.remove(subtaskDO.getUserId());
        }


    }

    /**
     * 调用发送邮件接口
     * */
    public void sendDescriptionsEmail(APGUserGeneratedSubtaskDO subtaskDO) {
        // 如果额度超限，发超限邮件
        APGUserGeneratedTaskDO taskDO = iapgUserGeneratedTaskService.getOne(new LambdaQueryWrapper<APGUserGeneratedTaskDO>().eq(APGUserGeneratedTaskDO::getUserId, subtaskDO.getUserId()));
        if (taskDO.getTaskStatus() == 3) {
            return;
        }


        //将payload转化为GenerateEmailVO类型数据
        try {
            Boolean b = iapgUserGeneratedSubtaskService.updateStatusById(subtaskDO.getSubtaskId(), 2);
            System.out.println("修改子任务状态为2是否正确：" + b);

            GenerateEmailVO generateEmailVO = OBJECT_MAPPER.readValue(subtaskDO.getPayload(), GenerateEmailVO.class);
            //根据用户id，获取对应数据
            APGUsersDO usersDO = iapgUsersService.getOne(new LambdaQueryWrapper<APGUsersDO>().eq(APGUsersDO::getId, subtaskDO.getUserId()));
            //调用发送邮件接口
            //获取该用户这一次任务的所有token值
            APGUserCounterDO userCounter = iapgUserCounterService.getUserCounter(usersDO.getShopName());

            //TODO: 调用发送邮件接口
            System.out.println("发送邮件， " + generateEmailVO.getEmail() + " 消耗token：" + userCounter.getChars());
            //将这次任务的token数清零
            iapgUserCounterService.updateCharsByUserId(usersDO.getId());
        } catch (Exception e) {
            appInsights.trackTrace( subtaskDO.getUserId() + " 用户 发送邮件接口 errors ：" + e);
            //删除限制
            GENERATE_SHOP.remove(subtaskDO.getUserId());
            // 将状态改为4
            iapgUserGeneratedSubtaskService.updateStatusById(subtaskDO.getSubtaskId(), 4);
            iapgUserGeneratedTaskService.updateStatusByUserId(subtaskDO.getUserId(), 4);
        } finally {
            //删除状态为2的子任务
            APGUserGeneratedSubtaskDO gs = iapgUserGeneratedSubtaskService.getById(subtaskDO.getSubtaskId());
            if (gs.getStatus() == 2) {
                iapgUserGeneratedSubtaskService.removeById(subtaskDO.getSubtaskId());
            }
            //删除限制
            GENERATE_SHOP.remove(subtaskDO.getUserId());
        }
        //将用户进度条转化为1，已完成
        iapgUserGeneratedTaskService.updateStatusByUserId(subtaskDO.getUserId(), 1);
    }
}
