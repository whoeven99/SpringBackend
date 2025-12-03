package com.bogdatech.logic;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.bogdatech.Service.ITranslationCounterService;
import com.bogdatech.Service.IUserIpService;
import com.bogdatech.Service.IUserSubscriptionsService;
import com.bogdatech.entity.DO.TranslationCounterDO;
import com.bogdatech.entity.DO.UserIpDO;
import com.bogdatech.entity.VO.IncludeCrawlerVO;
import com.bogdatech.entity.VO.NoCrawlerVO;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.repository.entity.UserIPCountDO;
import com.bogdatech.repository.repo.UserIPCountRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

@Service
public class UserIpService {
    @Autowired
    private IUserSubscriptionsService iUserSubscriptionsService;
    @Autowired
    private IUserIpService iUserIpService;
    @Autowired
    private TencentEmailService tencentEmailService;
    @Autowired
    private ITranslationCounterService iTranslationCounterService;
    @Autowired
    private UserIPCountRepo userIPCountRepo;

    public static final String ALL_LANGUAGE_IP_COUNT = "ALL_LANGUAGE_IP_COUNT"; // 所有语言ip计数
    public static final String ALL_CURRENCY_IP_COUNT = "ALL_CURRENCY_IP_COUNT"; // 所有货币ip计数
    public static final String NO_LANGUAGE_CODE = "NO_LANGUAGE_CODE_"; // 对应语言的ip计数
    public static final String NO_CURRENCY_CODE = "NO_CURRENCY_CODE_"; // 对应货币的ip计数

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
        appInsights.trackTrace("checkUserIp userIpDO = " + userIpDO);

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
//            appInsights.trackTrace("translationCounterDO = " + translationCounterDO);
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

    public BaseResponse<Object> includeCrawlerPrintLog(String shopName, IncludeCrawlerVO includeCrawlerVO) {
        appInsights.trackTrace(shopName + " " + includeCrawlerVO.getUaInformation() + " 原因 " + includeCrawlerVO.getUaReason());
        return new BaseResponse<>().CreateSuccessResponse(true);
    }

    public BaseResponse<Object> noCrawlerPrintLog(String shopName, NoCrawlerVO noCrawlerVO) {
        appInsights.trackTrace("状态码：" + noCrawlerVO.getStatus() + " , " + shopName + " 客户ip定位： " + noCrawlerVO.getUserIp()
                + " , 语言代码： " + noCrawlerVO.getLanguageCode() + " , 是否包含该语言： " + noCrawlerVO.getLanguageCodeStatus()
                + " , 货币代码： " + noCrawlerVO.getCurrencyCode() + " , 国家代码： " + noCrawlerVO.getCountryCode() + " , 是否包含该市场： "
                + noCrawlerVO.getCurrencyCodeStatus() + " , checkUserIp接口花费时间： " + noCrawlerVO.getCostTime() + " , ipApi接口花费时间： " + noCrawlerVO.getIpApiCostTime()
                + " , 错误信息： " + noCrawlerVO.getErrorMessage());

        // 获取该用户的所有ip计数，不同语言计数，不同货币计数
        List<UserIPCountDO> userIPCounts = userIPCountRepo.selectAllByShopName(shopName);

        // 将已有的记录转成 Map，便于判断是否存在
        Map<String, UserIPCountDO> countMap = userIPCounts.stream()
                .collect(Collectors.toMap(UserIPCountDO::getCountType, v -> v));

        // 批处理列表
        List<UserIPCountDO> toInsert = new ArrayList<>();
        List<UserIPCountDO> toUpdate = new ArrayList<>();

        // 所有需要维护的类型
        String langCode = noCrawlerVO.getLanguageCode();
        String currencyCode = noCrawlerVO.getCurrencyCode();

        // 1. ALL_LANGUAGE_IP_COUNT
        UserIPCountDO langAll = countMap.get(ALL_LANGUAGE_IP_COUNT);
        if (langAll != null) {
            langAll.setCountValue(langAll.getCountValue() + 1);
            toUpdate.add(langAll);
        } else {
            toInsert.add(new UserIPCountDO(shopName, ALL_LANGUAGE_IP_COUNT, 1));
        }

        // 2. ALL_CURRENCY_IP_COUNT
        UserIPCountDO currencyAll = countMap.get(ALL_CURRENCY_IP_COUNT);
        if (currencyAll != null) {
            currencyAll.setCountValue(currencyAll.getCountValue() + 1);
            toUpdate.add(currencyAll);
        } else {
            toInsert.add(new UserIPCountDO(shopName, ALL_CURRENCY_IP_COUNT, 1));
        }

        // 3. 按 languageCode 匹配
        UserIPCountDO langSpec = countMap.get(NO_LANGUAGE_CODE + langCode);
        if (langSpec != null && !noCrawlerVO.getLanguageCodeStatus()) {
            langSpec.setCountValue(langSpec.getCountValue() + 1);
            toUpdate.add(langSpec);
        } else if (langSpec == null){
            toInsert.add(new UserIPCountDO(shopName, NO_LANGUAGE_CODE + langCode, 1));
        }

        // 4. 按 currencyCode 匹配
        UserIPCountDO currencySpec = countMap.get(NO_CURRENCY_CODE + currencyCode);
        if (currencySpec != null && !noCrawlerVO.getCurrencyCodeStatus()) {
            currencySpec.setCountValue(currencySpec.getCountValue() + 1);
            toUpdate.add(currencySpec);
        } else if (currencySpec == null){
            toInsert.add(new UserIPCountDO(shopName, NO_CURRENCY_CODE + currencyCode, 1));
        }

        // 执行批量 SQL
        if (!toUpdate.isEmpty()) {
            userIPCountRepo.updateBatchById(toUpdate);
        }
        if (!toInsert.isEmpty()) {
            userIPCountRepo.saveBatchUserIps(toInsert);
        }

        return new BaseResponse<>().CreateSuccessResponse(true);
    }
}
