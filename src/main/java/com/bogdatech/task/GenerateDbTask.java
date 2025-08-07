package com.bogdatech.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bogdatech.Service.*;
import com.bogdatech.entity.DO.APGUserCounterDO;
import com.bogdatech.entity.DO.APGUserGeneratedSubtaskDO;
import com.bogdatech.entity.DO.APGUserGeneratedTaskDO;
import com.bogdatech.entity.DO.APGUsersDO;
import com.bogdatech.entity.DTO.ProductDTO;
import com.bogdatech.entity.VO.GenerateDescriptionVO;
import com.bogdatech.entity.VO.GenerateDescriptionsVO;
import com.bogdatech.entity.VO.GenerateEmailVO;
import com.bogdatech.exception.ClientException;
import com.bogdatech.logic.GenerateDescriptionService;
import com.bogdatech.logic.TencentEmailService;
import com.bogdatech.utils.CharacterCountUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.validation.constraints.Email;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.bogdatech.constants.TranslateConstants.EMAIL;
import static com.bogdatech.logic.APGUserGeneratedTaskService.GENERATE_STATE_BAR;
import static com.bogdatech.logic.APGUserGeneratedTaskService.INITIALIZATION;
import static com.bogdatech.logic.TranslateService.*;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

@Service
public class GenerateDbTask {

    private final IAPGUserGeneratedSubtaskService iapgUserGeneratedSubtaskService;
    private final GenerateDescriptionService generateDescriptionService;
    private final IAPGUsersService iapgUsersService;
    private final IAPGUserPlanService iapgUserPlanService;
    private final IAPGUserCounterService iapgUserCounterService;
    private final IAPGUserGeneratedTaskService iapgUserGeneratedTaskService;
    private final TencentEmailService tencentEmailService;
    public static final Set<Long> GENERATE_SHOP = ConcurrentHashMap.newKeySet(); //判断用户是否正在生成描述
    public static final ConcurrentHashMap<Long, String> GENERATE_SHOP_BAR = new ConcurrentHashMap<>(); //判断用户正在生成描述
    public static final ConcurrentHashMap<Long, Boolean> GENERATE_SHOP_STOP_FLAG = new ConcurrentHashMap<>(); //判断用户是否停止生成描述

    @Autowired
    public GenerateDbTask(IAPGUserGeneratedSubtaskService iapgUserGeneratedSubtaskService, GenerateDescriptionService generateDescriptionService, IAPGUsersService iapgUsersService, IAPGUserPlanService iapgUserPlanService, IAPGUserCounterService iapgUserCounterService, IAPGUserGeneratedTaskService iapgUserGeneratedTaskService, TencentEmailService tencentEmailService) {
        this.iapgUserGeneratedSubtaskService = iapgUserGeneratedSubtaskService;
        this.generateDescriptionService = generateDescriptionService;
        this.iapgUsersService = iapgUsersService;
        this.iapgUserPlanService = iapgUserPlanService;
        this.iapgUserCounterService = iapgUserCounterService;
        this.iapgUserGeneratedTaskService = iapgUserGeneratedTaskService;
        this.tencentEmailService = tencentEmailService;
    }

    // 每3秒钟检查一次是否有闲置线程
    @Scheduled(fixedDelay = 3000)
    public void scanAndGenerateSubtask() {
        // 获取所有status为0的数据
        List<APGUserGeneratedSubtaskDO> list = iapgUserGeneratedSubtaskService.list(new LambdaQueryWrapper<APGUserGeneratedSubtaskDO>().eq(APGUserGeneratedSubtaskDO::getStatus, 0).orderBy(true, true, APGUserGeneratedSubtaskDO::getCreateTime));
        // 循环异步翻译
        for (APGUserGeneratedSubtaskDO subtaskDO : list
        ) {
            // 一个用户同一时间只能翻译一个
            if (!GENERATE_SHOP.contains(subtaskDO.getUserId())) {
                GENERATE_SHOP.add(subtaskDO.getUserId());
                executorService.submit(() -> {
                    appInsights.trackTrace("用户 " + subtaskDO.getUserId() + " 开始生成 子任务： " + subtaskDO.getSubtaskId());
                    try {
                        // 做判断，是邮件还是生成任务
                        if (subtaskDO.getPayload().contains("\"email\":\"EMAIL\"")) {
                            // 调用发送邮件接口
                            sendDescriptionsEmail(subtaskDO);
                            GENERATE_STATE_BAR.remove(subtaskDO.getUserId());
                            return;
                        }
                        // 调用单条生成接口
                        fixGenerateSubtask(subtaskDO);
                    } catch (Exception e) {
                        appInsights.trackTrace("用户 " + subtaskDO.getUserId() + " 生成 子任务： " + subtaskDO.getSubtaskId() + " errors ：" + e);
                    }
                });
            }
        }

    }

    /**
     * 调用单条生成接口
     */
    public void fixGenerateSubtask(APGUserGeneratedSubtaskDO subtaskDO) {
        // 修改子任务状态为2
        iapgUserGeneratedSubtaskService.updateStatusById(subtaskDO.getSubtaskId(), 2);
        // 获取用户数据
        APGUsersDO usersDO = iapgUsersService.getOne(new LambdaQueryWrapper<APGUsersDO>().eq(APGUsersDO::getId, subtaskDO.getUserId()));
        GENERATE_STATE_BAR.put(usersDO.getId(), INITIALIZATION);
        // 获取用户最大额度
        Integer userMaxLimit = iapgUserPlanService.getUserMaxLimit(usersDO.getId());
        // 将String数据，处理成 GenerateDescriptionVO数据
        GenerateDescriptionVO gvo;
        CharacterCountUtils counter = new CharacterCountUtils();
        try {
            if (GENERATE_SHOP_STOP_FLAG.get(usersDO.getId())) {
                return;
            }
            gvo = OBJECT_MAPPER.readValue(subtaskDO.getPayload(), GenerateDescriptionVO.class);
            ProductDTO product = generateDescriptionService.getProductsQueryByProductId(gvo.getProductId(), usersDO.getShopName(), usersDO.getAccessToken());
            generateDescriptionService.generateDescription(usersDO, gvo, counter, userMaxLimit, product);
        } catch (JsonProcessingException e) {
            appInsights.trackTrace(usersDO.getShopName() + " 用户 fixGenerateSubtask errors ：" + e);
            //将该任务状态改为4
            iapgUserGeneratedSubtaskService.updateStatusById(subtaskDO.getSubtaskId(), 4);
        } catch (ClientException e1) {
            GENERATE_SHOP_STOP_FLAG.put(usersDO.getId(), true);
            iapgUserGeneratedSubtaskService.updateStatusById(subtaskDO.getSubtaskId(), 3);
            iapgUserGeneratedTaskService.updateStatusByUserId(usersDO.getId(), 3);
            appInsights.trackTrace(usersDO.getShopName() + " 用户 fixGenerateSubtask errors ：" + e1);
            //发送对应翻译中断的邮件
            sendAPGTaskInterruptEmail(usersDO);
        } catch (Exception e2) {
            iapgUserGeneratedSubtaskService.updateStatusById(subtaskDO.getSubtaskId(), 4);
            appInsights.trackTrace(usersDO.getShopName() + " 用户 fixGenerateSubtask errors ：" + e2);
        } finally {
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
     */
    public void sendDescriptionsEmail(APGUserGeneratedSubtaskDO subtaskDO) {
        // 如果额度超限，发超限邮件
        APGUserGeneratedTaskDO taskDO = iapgUserGeneratedTaskService.getOne(new LambdaQueryWrapper<APGUserGeneratedTaskDO>().eq(APGUserGeneratedTaskDO::getUserId, subtaskDO.getUserId()));
        if (taskDO.getTaskStatus() == 3) {
            iapgUserGeneratedSubtaskService.updateStatusById(subtaskDO.getSubtaskId(), 3);
            //删除限制
            GENERATE_SHOP.remove(subtaskDO.getUserId());
            return;
        }
        //根据用户id，获取对应数据
        APGUsersDO usersDO = iapgUsersService.getOne(new LambdaQueryWrapper<APGUsersDO>().eq(APGUsersDO::getId, subtaskDO.getUserId()));
        //将payload转化为GenerateEmailVO类型数据
        try {
            iapgUserGeneratedSubtaskService.updateStatusById(subtaskDO.getSubtaskId(), 2);
            GenerateEmailVO generateEmailVO = OBJECT_MAPPER.readValue(subtaskDO.getPayload(), GenerateEmailVO.class);

            //调用发送邮件接口
            //获取该用户这一次任务的所有token值
            APGUserCounterDO userCounter = iapgUserCounterService.getUserCounter(usersDO.getShopName());

            //调用发送邮件接口
            //对taskDO的taskModel做处理，获取空格前的数据
            String taskModel = taskDO.getTaskModel();
            if (taskModel.contains(" ")) {
                taskModel = taskModel.substring(0, taskModel.indexOf(" "));
            }
            //获取用户最大额度限制
            Integer userMaxLimit = iapgUserPlanService.getUserMaxLimit(usersDO.getId());
            tencentEmailService.sendAPGSuccessEmail(usersDO.getEmail(), usersDO.getId(), taskModel, usersDO.getFirstName(), subtaskDO.getCreateTime(), userCounter.getChars(), generateEmailVO.getProductIds().length, userMaxLimit);
            appInsights.trackTrace("用户 " + usersDO.getShopName() + "  发送邮件， " + generateEmailVO.getEmail() + " 消耗token：" + userCounter.getChars());
            //将这次任务的token数清零
            iapgUserCounterService.updateCharsByUserId(usersDO.getId());
        } catch (Exception e) {
            appInsights.trackTrace(subtaskDO.getUserId() + " 用户 发送邮件接口 errors ：" + e);
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
            //将这次任务的token数清零
            iapgUserCounterService.updateCharsByUserId(usersDO.getId());
        }
        //将用户进度条转化为1，已完成
        iapgUserGeneratedTaskService.updateStatusByUserId(subtaskDO.getUserId(), 1);
    }

    /**
     * 发送对应翻译中断的邮件
     * 获取用户主任务的taskId，然后获取子任务所有状态3，做遍历对比，获取已完成的产品数，剩余产品数
     * 获取当前的token
     * */
    public void sendAPGTaskInterruptEmail(APGUsersDO usersDO) {
        //获取用户主任务的taskId，然后获取子任务所有状态3，做遍历对比，获取已完成的产品数，剩余产品数
        APGUserGeneratedTaskDO apgUserGeneratedTaskDO = iapgUserGeneratedTaskService.getOne(new LambdaQueryWrapper<APGUserGeneratedTaskDO>().eq(APGUserGeneratedTaskDO::getUserId, usersDO.getId()));
        String taskData = apgUserGeneratedTaskDO.getTaskData();
        //获取用户消耗数据
        APGUserCounterDO userCounter = iapgUserCounterService.getUserCounter(usersDO.getShopName());
        //转化数据类型
        try {
            GenerateDescriptionsVO generateDescriptionsVO = OBJECT_MAPPER.readValue(taskData, GenerateDescriptionsVO.class);
            Set<String> productIds = Arrays.stream(generateDescriptionsVO.getProductIds()).collect(Collectors.toSet());
            //获取状态为3的任务id
            List<APGUserGeneratedSubtaskDO> status3List = iapgUserGeneratedSubtaskService.list(new LambdaQueryWrapper<APGUserGeneratedSubtaskDO>().eq(APGUserGeneratedSubtaskDO::getUserId, usersDO.getId()).eq(APGUserGeneratedSubtaskDO::getStatus, 3));
            Iterator<APGUserGeneratedSubtaskDO> iterator = status3List.iterator();
            int completeProductsSize = 0;
            while (iterator.hasNext()) {
                APGUserGeneratedSubtaskDO subtask = iterator.next();
                if (subtask.getPayload().contains(EMAIL)) {
                    continue;
                }
                //解析payload，获取里面的productId
                GenerateDescriptionVO generateDescriptionVO = OBJECT_MAPPER.readValue(subtask.getPayload(), GenerateDescriptionVO.class);
                if (productIds.contains(generateDescriptionVO.getProductId())) {
                    completeProductsSize++;
                    break;
                }
            }

            //计数list的数量
            GENERATE_STATE_BAR.remove(usersDO.getId());
            tencentEmailService.sendAPGTaskInterruptEmail(usersDO, completeProductsSize, productIds.size() - completeProductsSize, userCounter.getChars());
        } catch (Exception e) {
            appInsights.trackTrace("用户 " + usersDO.getShopName() + "  发送失败邮件接口 errors ：" + e);
            appInsights.trackException(e);
        } finally {
            //将这次任务的token数清零
            iapgUserCounterService.updateCharsByUserId(usersDO.getId());
        }
    }
}
