package com.bogdatech.logic;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.bogdatech.Service.ITranslationCounterService;
import com.bogdatech.Service.IUserIpService;
import com.bogdatech.Service.IUserSubscriptionsService;
import com.bogdatech.entity.DO.TranslationCounterDO;
import com.bogdatech.entity.DO.UserIpDO;
import com.bogdatech.mapper.UserIpMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

@Service
public class UserIpService {
    private final IUserSubscriptionsService iUserSubscriptionsService;
    private final IUserIpService iUserIpService;
    private final TencentEmailService tencentEmailService;
    private final ITranslationCounterService iTranslationCounterService;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    public UserIpService(IUserSubscriptionsService iUserSubscriptionsService, IUserIpService iUserIpService, TencentEmailService tencentEmailService, ITranslationCounterService iTranslationCounterService, TransactionTemplate transactionTemplate) {
        this.iUserSubscriptionsService = iUserSubscriptionsService;
        this.iUserIpService = iUserIpService;
        this.tencentEmailService = tencentEmailService;
        this.iTranslationCounterService = iTranslationCounterService;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 检查额度是否足够，足够+1. 到达相关百分比，发邮件
     */
    @Transactional
    public Boolean checkUserIp(String shopName) {
        // 使用事务确保数据一致性
        // 获取用户计划,加锁查询
        Integer userSubscriptionPlan = iUserSubscriptionsService.getUserSubscriptionPlan(shopName);
        int freeIp = switch (userSubscriptionPlan) {
            case 4 -> 10000;
            case 5 -> 25000;
            case 6 -> 50000;
            default -> 500;
        };

        // 使用行锁获取用户IP数据，防止并发修改
        UserIpDO userIpDO = iUserIpService.selectByShopNameForUpdate(shopName);
        if (userIpDO == null) {
            return false;
        }
        appInsights.trackTrace("userIpDO = " + userIpDO);

        long currentTimes = userIpDO.getTimes();

        // 判断是否达到90%并发送第一封邮件
        int percent90 = (int) (freeIp * 0.9);
        if (currentTimes >= percent90 && currentTimes < freeIp && !Boolean.TRUE.equals(userIpDO.getFirstEmail())) {
            if (tencentEmailService.sendEmailByIpRunningOut(shopName)) {
                userIpDO.setFirstEmail(true);
                userIpDO.setTimes(currentTimes + 1);
                userIpDO.setAllTimes(userIpDO.getAllTimes() + 1);
                return iUserIpService.update(userIpDO, new UpdateWrapper<UserIpDO>().eq("shop_name", shopName).eq("first_email", false));
            }
        }

        // 判断是否达到100%并发送第二封邮件
        if (currentTimes > freeIp && !Boolean.TRUE.equals(userIpDO.getSecondEmail())) {
            if (tencentEmailService.sendEmailByIpOut(shopName)) {
                userIpDO.setSecondEmail(true);
                userIpDO.setTimes(currentTimes + 1);
                userIpDO.setAllTimes(userIpDO.getAllTimes() + 1);
                return iUserIpService.update(userIpDO, new UpdateWrapper<UserIpDO>().eq("shop_name", shopName).eq("second_email", false));
            }
        }

        // 超出免费IP时检查用户额度
        if (currentTimes > freeIp) {
            //加锁查询检查用户额度是否足够
            TranslationCounterDO translationCounterDO = iTranslationCounterService.getOneForUpdate(shopName);
            appInsights.trackTrace("translationCounterDO = " + translationCounterDO);
            if (translationCounterDO == null) {
                return false;
            }

            Integer maxChars = iTranslationCounterService.getMaxCharsByShopName(shopName);

            if (translationCounterDO.getUsedChars() < maxChars) {
                translationCounterDO.setUsedChars(translationCounterDO.getUsedChars() + 100);
//                userIpDO.setTimes(currentTimes + 1);
                userIpDO.setAllTimes(userIpDO.getAllTimes() + 1);
                iUserIpService.updateById(userIpDO);
                iTranslationCounterService.updateById(translationCounterDO);
                return true;
            } else {
                return false;
            }
        }

        // 正常增长次数
        userIpDO.setTimes(currentTimes + 1);
        userIpDO.setAllTimes(userIpDO.getAllTimes() + 1);
        return iUserIpService.updateById(userIpDO);
    }

}
