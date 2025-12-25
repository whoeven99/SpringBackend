package com.bogda.task.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bogda.common.service.*;
import com.bogda.common.constants.TranslateConstants;
import com.bogda.common.entity.DO.APGUserCounterDO;
import com.bogda.common.entity.DO.APGUserGeneratedSubtaskDO;
import com.bogda.common.entity.DO.APGUserGeneratedTaskDO;
import com.bogda.common.entity.DO.APGUsersDO;
import com.bogda.common.entity.DTO.ProductDTO;
import com.bogda.common.entity.VO.GenerateDescriptionVO;
import com.bogda.common.entity.VO.GenerateDescriptionsVO;
import com.bogda.common.entity.VO.GenerateEmailVO;
import com.bogda.common.logic.APGUserGeneratedTaskService;
import com.bogda.common.logic.GenerateDescriptionService;
import com.bogda.common.logic.TencentEmailService;
import com.bogda.common.logic.TranslateService;
import com.bogda.common.utils.CaseSensitiveUtils;
import com.bogda.common.utils.CharacterCountUtils;
import com.bogda.common.utils.JsonUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.genai.errors.ClientException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@EnableScheduling
public class GenerateDbTask {
    @Autowired
    private IAPGUserGeneratedSubtaskService iapgUserGeneratedSubtaskService;
    @Autowired
    private GenerateDescriptionService generateDescriptionService;
    @Autowired
    private IAPGUsersService iapgUsersService;
    @Autowired
    private IAPGUserPlanService iapgUserPlanService;
    @Autowired
    private IAPGUserCounterService iapgUserCounterService;
    @Autowired
    private IAPGUserGeneratedTaskService iapgUserGeneratedTaskService;
    @Autowired
    private TencentEmailService tencentEmailService;

    // 每3秒钟检查一次是否有闲置线程
    @Scheduled(fixedDelay = 3000)
    public void scanAndGenerateSubtask() {
        // 获取所有status为0的数据
        List<APGUserGeneratedSubtaskDO> list = iapgUserGeneratedSubtaskService.list(new LambdaQueryWrapper<APGUserGeneratedSubtaskDO>().eq(APGUserGeneratedSubtaskDO::getStatus, 0).orderBy(true, true, APGUserGeneratedSubtaskDO::getCreateTime));
        // 循环异步翻译
        for (APGUserGeneratedSubtaskDO subtaskDO : list
        ) {
            // 一个用户同一时间只能翻译一个
            if (!APGUserGeneratedTaskService.GENERATE_SHOP.contains(subtaskDO.getUserId())) {
                APGUserGeneratedTaskService.GENERATE_SHOP.add(subtaskDO.getUserId());
                TranslateService.executorService.submit(() -> {
                    CaseSensitiveUtils.appInsights.trackTrace("用户 " + subtaskDO.getUserId() + " 开始生成 子任务： " + subtaskDO.getSubtaskId());
                    try {
                        // 做判断，是邮件还是生成任务
                        if (subtaskDO.getPayload().contains("\"email\":\"EMAIL\"")) {
                            // 调用发送邮件接口
                            sendDescriptionsEmail(subtaskDO);
                            APGUserGeneratedTaskService.GENERATE_STATE_BAR.remove(subtaskDO.getUserId());
                            return;
                        }
                        // 调用单条生成接口
                        fixGenerateSubtask(subtaskDO);
                    } catch (Exception e) {
                        CaseSensitiveUtils.appInsights.trackTrace("用户 " + subtaskDO.getUserId() + " 生成 子任务： " + subtaskDO.getSubtaskId() + " errors ：" + e);
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
        APGUserGeneratedTaskService.GENERATE_STATE_BAR.put(usersDO.getId(), APGUserGeneratedTaskService.INITIALIZATION);
        // 获取用户最大额度
        Integer userMaxLimit = iapgUserPlanService.getUserMaxLimit(usersDO.getId());
        // 将String数据，处理成 GenerateDescriptionVO数据
        GenerateDescriptionVO gvo;
        CharacterCountUtils counter = new CharacterCountUtils();
        try {
            if (APGUserGeneratedTaskService.GENERATE_SHOP_STOP_FLAG.get(usersDO.getId())) {
                return;
            }
            gvo = JsonUtils.OBJECT_MAPPER.readValue(subtaskDO.getPayload(), GenerateDescriptionVO.class);
            ProductDTO product = generateDescriptionService.getProductsQueryByProductId(gvo.getProductId(), usersDO.getShopName(), usersDO.getAccessToken());
            generateDescriptionService.generateDescription(usersDO, gvo, counter, userMaxLimit, product);
        } catch (JsonProcessingException e) {
            CaseSensitiveUtils.appInsights.trackTrace(usersDO.getShopName() + " 用户 fixGenerateSubtask errors ：" + e);
            //将该任务状态改为4
            iapgUserGeneratedSubtaskService.updateStatusById(subtaskDO.getSubtaskId(), 4);
        } catch (ClientException e1) {
            APGUserGeneratedTaskService.GENERATE_SHOP_STOP_FLAG.put(usersDO.getId(), true);
            iapgUserGeneratedSubtaskService.updateStatusById(subtaskDO.getSubtaskId(), 3);
            iapgUserGeneratedTaskService.updateStatusByUserId(usersDO.getId(), 3);
            CaseSensitiveUtils.appInsights.trackTrace(usersDO.getShopName() + " 用户 fixGenerateSubtask errors ：" + e1);
            //发送对应翻译中断的邮件
            sendAPGTaskInterruptEmail(usersDO);
        } catch (Exception e2) {
            iapgUserGeneratedSubtaskService.updateStatusById(subtaskDO.getSubtaskId(), 4);
            CaseSensitiveUtils.appInsights.trackTrace(usersDO.getShopName() + " 用户 fixGenerateSubtask errors ：" + e2);
        } finally {
            //删除状态为2的子任务
            APGUserGeneratedSubtaskDO gs = iapgUserGeneratedSubtaskService.getById(subtaskDO.getSubtaskId());
            if (gs.getStatus() == 2) {
                iapgUserGeneratedSubtaskService.removeById(subtaskDO.getSubtaskId());
            }
            //删除限制
            APGUserGeneratedTaskService.GENERATE_SHOP.remove(subtaskDO.getUserId());
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
            APGUserGeneratedTaskService.GENERATE_SHOP.remove(subtaskDO.getUserId());
            return;
        }
        //根据用户id，获取对应数据
        APGUsersDO usersDO = iapgUsersService.getOne(new LambdaQueryWrapper<APGUsersDO>().eq(APGUsersDO::getId, subtaskDO.getUserId()));
        //将payload转化为GenerateEmailVO类型数据
        try {
            iapgUserGeneratedSubtaskService.updateStatusById(subtaskDO.getSubtaskId(), 2);
            GenerateEmailVO generateEmailVO = JsonUtils.OBJECT_MAPPER.readValue(subtaskDO.getPayload(), GenerateEmailVO.class);

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
            //获取用户剩余额度
            Integer userSurplusLimit = userMaxLimit - userCounter.getUserToken();
            if (userSurplusLimit < 0) {
                userSurplusLimit = 0;
            }
            tencentEmailService.sendAPGSuccessEmail(usersDO.getEmail(), usersDO.getId(), taskModel, usersDO.getFirstName(), subtaskDO.getCreateTime(), userCounter.getChars(), generateEmailVO.getProductIds().length, userSurplusLimit);
            CaseSensitiveUtils.appInsights.trackTrace("用户 " + usersDO.getShopName() + "  发送邮件， " + generateEmailVO.getEmail() + " 消耗token ：" + userCounter.getChars());
            //将这次任务的token数清零
            iapgUserCounterService.updateCharsByUserId(usersDO.getId());
        } catch (Exception e) {
            CaseSensitiveUtils.appInsights.trackTrace(subtaskDO.getUserId() + " 用户 发送邮件接口 errors ：" + e);
            //删除限制
            APGUserGeneratedTaskService.GENERATE_SHOP.remove(subtaskDO.getUserId());
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
            APGUserGeneratedTaskService.GENERATE_SHOP.remove(subtaskDO.getUserId());
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
     */
    public void sendAPGTaskInterruptEmail(APGUsersDO usersDO) {
        //获取用户主任务的taskId，然后获取子任务所有状态3，做遍历对比，获取已完成的产品数，剩余产品数
        APGUserGeneratedTaskDO apgUserGeneratedTaskDO = iapgUserGeneratedTaskService.getOne(new LambdaQueryWrapper<APGUserGeneratedTaskDO>().eq(APGUserGeneratedTaskDO::getUserId, usersDO.getId()));
        String taskData = apgUserGeneratedTaskDO.getTaskData();
        //获取用户消耗数据
        APGUserCounterDO userCounter = iapgUserCounterService.getUserCounter(usersDO.getShopName());
        //转化数据类型
        try {
            GenerateDescriptionsVO generateDescriptionsVO = JsonUtils.OBJECT_MAPPER.readValue(taskData, GenerateDescriptionsVO.class);
            Set<String> productIds = Arrays.stream(generateDescriptionsVO.getProductIds()).collect(Collectors.toSet());
            //获取状态为3的任务id
            List<APGUserGeneratedSubtaskDO> status3List = iapgUserGeneratedSubtaskService.list(new LambdaQueryWrapper<APGUserGeneratedSubtaskDO>().eq(APGUserGeneratedSubtaskDO::getUserId, usersDO.getId()).eq(APGUserGeneratedSubtaskDO::getStatus, 3));
            Iterator<APGUserGeneratedSubtaskDO> iterator = status3List.iterator();
            int completeProductsSize = 0;
            while (iterator.hasNext()) {
                APGUserGeneratedSubtaskDO subtask = iterator.next();
                if (subtask.getPayload().contains(TranslateConstants.EMAIL)) {
                    continue;
                }
                //解析payload，获取里面的productId
                GenerateDescriptionVO generateDescriptionVO = JsonUtils.OBJECT_MAPPER.readValue(subtask.getPayload(), GenerateDescriptionVO.class);
                if (productIds.contains(generateDescriptionVO.getProductId())) {
                    completeProductsSize++;
                    break;
                }
            }

            //计数list的数量
            APGUserGeneratedTaskService.GENERATE_STATE_BAR.remove(usersDO.getId());
            tencentEmailService.sendAPGTaskInterruptEmail(usersDO, completeProductsSize, productIds.size() - completeProductsSize, userCounter.getChars());
        } catch (Exception e) {
            CaseSensitiveUtils.appInsights.trackTrace("用户 " + usersDO.getShopName() + "  发送失败邮件接口 errors ：" + e);
            CaseSensitiveUtils.appInsights.trackException(e);
        } finally {
            //将这次任务的token数清零
            iapgUserCounterService.updateCharsByUserId(usersDO.getId());
        }
    }
}
