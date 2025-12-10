package com.bogdatech.logic;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.bogdatech.Service.*;
import com.bogdatech.entity.DO.CurrenciesDO;
import com.bogdatech.entity.DO.TranslationCounterDO;
import com.bogdatech.entity.DO.UserIpDO;
import com.bogdatech.entity.DO.WidgetConfigurationsDO;
import com.bogdatech.entity.VO.IncludeCrawlerVO;
import com.bogdatech.entity.VO.IpRedirectionVO;
import com.bogdatech.entity.VO.NoCrawlerVO;
import com.bogdatech.entity.VO.WidgetReturnVO;
import com.bogdatech.logic.token.UserTokenService;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.repository.entity.UserIPCountDO;
import com.bogdatech.repository.entity.UserIPRedirectionDO;
import com.bogdatech.repository.repo.UserIPCountRepo;
import com.bogdatech.repository.repo.UserIPRedirectionRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.function.BiConsumer;
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
    private UserIPRedirectionRepo userIPRedirectionRepo;
    @Autowired
    private IWidgetConfigurationsService iWidgetConfigurationsService;
    @Autowired
    private UserIPCountRepo userIPCountRepo;
    @Autowired
    private UserTokenService userTokenService;
    @Autowired
    private ITranslatesService iTranslatesService;
    @Autowired
    private ICurrenciesService iCurrenciesService;

    public static final String ALL_LANGUAGE_IP_COUNT = "ALL_LANGUAGE_IP_COUNT"; // 所有语言ip计数
    public static final String ALL_CURRENCY_IP_COUNT = "ALL_CURRENCY_IP_COUNT"; // 所有货币ip计数
    public static final String NO_LANGUAGE_CODE = "NO_LANGUAGE_CODE_"; // 对应语言的ip计数
    public static final String NO_CURRENCY_CODE = "NO_CURRENCY_CODE_"; // 对应货币的ip计数

    /**
     * 检查额度是否足够，足够+1. 到达相关百分比，发邮件
     */
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
            if (translationCounterDO == null) {
                return false;
            }

            Integer maxChars = iTranslationCounterService.getMaxCharsByShopName(shopName);

            if (translationCounterDO.getUsedChars() < maxChars) {
                translationCounterDO.setUsedChars(translationCounterDO.getUsedChars() + 100);
//                userIpDO.setTimes(currentTimes + 1);
                userIpDO.setAllTimes(userIpDO.getAllTimes() + 1);
                iUserIpService.updateById(userIpDO);
                userTokenService.addUsedToken(shopName, 100);
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

    public BaseResponse<Object> noCrawlerPrintLog(String shopName, NoCrawlerVO noCrawlerVO) {
        appInsights.trackTrace("状态码：" + noCrawlerVO.getStatus() + " , " + shopName + " 客户ip定位： " + noCrawlerVO.getUserIp()
                + " , 语言代码： " + noCrawlerVO.getLanguageCode() + " , 是否包含该语言： " + noCrawlerVO.getLanguageCodeStatus()
                + " , 货币代码： " + noCrawlerVO.getCurrencyCode() + " , 国家代码： " + noCrawlerVO.getCountryCode() + " , 是否包含该市场： "
                + noCrawlerVO.getCurrencyCodeStatus() + " , checkUserIp接口花费时间： " + noCrawlerVO.getCostTime() + " , ipApi接口花费时间： " + noCrawlerVO.getIpApiCostTime()
                + " , 错误信息： " + noCrawlerVO.getErrorMessage());

        // --- 查询已有计数记录 ---
        Map<String, UserIPCountDO> countMap = userIPCountRepo
                .selectAllByShopName(shopName)
                .stream()
                .collect(Collectors.toMap(UserIPCountDO::getCountType, v -> v));

        List<UserIPCountDO> toInsert = new ArrayList<>();
        List<UserIPCountDO> toUpdate = new ArrayList<>();

        // 公共方法：处理计数更新/插入
        BiConsumer<String, Boolean> handleCount = (type, increment) -> {
            UserIPCountDO record = countMap.get(type);
            if (record != null) {
                if (increment) {
                    record.setCountValue(record.getCountValue() + 1);
                }
                toUpdate.add(record);
            } else {
                toInsert.add(new UserIPCountDO(shopName, type, increment ? 1 : 0));
            }
        };

        // 1. ALL_LANGUAGE_IP_COUNT
        handleCount.accept(ALL_LANGUAGE_IP_COUNT, true);

        // 2. ALL_CURRENCY_IP_COUNT
        handleCount.accept(ALL_CURRENCY_IP_COUNT, true);

        // 3. 处理语言统计（含 pt 特殊规则）
        List<String> shopLangs = iTranslatesService.selectTargetByShopName(shopName);
        String langCode = noCrawlerVO.getLanguageCode();

        String finalLangKey;
        if ("pt".equals(langCode)) {
            finalLangKey = NO_LANGUAGE_CODE + langCode + "-" + noCrawlerVO.getCountryCode();
            langCode = langCode + "-" + noCrawlerVO.getCountryCode();
        } else {
            finalLangKey = NO_LANGUAGE_CODE + langCode;
        }

        boolean langExists = shopLangs.contains(langCode);
        boolean shouldIncLang = !noCrawlerVO.getLanguageCodeStatus() && !langExists;

        handleCount.accept(finalLangKey, shouldIncLang);

        // 4. 处理货币统计
        List<String> shopCurrencies = iCurrenciesService.selectByShopName(shopName)
                .stream().map(CurrenciesDO::getCurrencyCode).toList();

        String currencyCode = noCrawlerVO.getCurrencyCode();
        String currencyKey = NO_CURRENCY_CODE + currencyCode;

        boolean currencyExists = shopCurrencies.contains(currencyCode);
        boolean shouldIncCurrency = !noCrawlerVO.getCurrencyCodeStatus() && !currencyExists;

        handleCount.accept(currencyKey, shouldIncCurrency);

        // 执行批处理
        if (!toUpdate.isEmpty()) {
            userIPCountRepo.updateBatchById(toUpdate);
        }
        if (!toInsert.isEmpty()) {
            userIPCountRepo.saveBatchUserIps(toInsert);
        }

        return new BaseResponse<>().CreateSuccessResponse(true);
    }

    public BaseResponse<Object> includeCrawlerPrintLog(String shopName, IncludeCrawlerVO includeCrawlerVO) {
        appInsights.trackTrace(shopName + " " + includeCrawlerVO.getUaInformation() + " 原因 " + includeCrawlerVO.getUaReason());
        return new BaseResponse<>().CreateSuccessResponse(true);
    }

    /**
     * 批量存储ip跳转数据
     */
    public BaseResponse<Object> syncUserIp(String shopName, List<UserIPRedirectionDO> userIPList) {
        // 参数校验
        if (CollectionUtils.isEmpty(userIPList)) {
            return new BaseResponse<>().CreateErrorResponse("userIP list cannot be empty");
        }

        // 强制覆盖 shopName
        userIPList.forEach(item -> item.setShopName(shopName));

        // 获取已有的所有记录
        List<UserIPRedirectionDO> dbAll = userIPRedirectionRepo.selectIpRedirectionByShopNameAll(shopName);

        // 构建匹配 Map
        Map<String, UserIPRedirectionDO> dbMap = CollectionUtils.isEmpty(dbAll)
                ? new HashMap<>()
                : dbAll.stream().collect(Collectors.toMap(
                this::buildKey,
                item -> item,
                (a, b) -> a
        ));

        List<UserIPRedirectionDO> toInsert = new ArrayList<>();
        List<UserIPRedirectionDO> toDelete = new ArrayList<>();

        // 匹配逻辑
        for (UserIPRedirectionDO input : userIPList) {
            String key = buildKey(input);
            UserIPRedirectionDO exist = dbMap.get(key);

            if (exist == null) {
                // 插入新纪录
                input.setIsDeleted(false);
                toInsert.add(input);
            }
        }

        // 构建请求的 Map，用于快速判断哪些需要删除
        Map<String, UserIPRedirectionDO> requestMap = userIPList.stream()
                .collect(Collectors.toMap(
                        this::buildKey,
                        item -> item,
                        (a, b) -> a
                ));

        // 找出 toDelete = dbAll - userIPList
        for (UserIPRedirectionDO dbItem : dbAll) {
            String key = buildKey(dbItem);
            if (!requestMap.containsKey(key)) {
                toDelete.add(dbItem);
            }
        }

        // 执行批量插入
        if (!toInsert.isEmpty()) {
            userIPRedirectionRepo.saveIpRedirectList(toInsert);
        }

        // 批量删除
        if (!toDelete.isEmpty()) {
            // 你需要提供 delete 接口（软删或真删任选）
            userIPRedirectionRepo.batchDeleteIpRedirect(toDelete);
        }

        // 获取更新后的 DB 数据
        List<UserIPRedirectionDO> dbFinal = userIPRedirectionRepo.selectIpRedirectionByShopName(shopName);
        if (CollectionUtils.isEmpty(dbFinal)) {
            return new BaseResponse<>().CreateErrorResponse("No data found");
        }

        // 转成 map 快速匹配
        Map<String, UserIPRedirectionDO> dbFinalMap = dbFinal.stream().collect(
                Collectors.toMap(this::buildKey, item -> item, (a, b) -> a)
        );

        // 返回本次操作成功的数据（关键点：按提交顺序返回最新状态）
        List<UserIPRedirectionDO> finalResult = userIPList.stream()
                .map(item -> dbFinalMap.get(buildKey(item)))
                .filter(Objects::nonNull) // 安全过滤
                .toList();
        return new BaseResponse<>().CreateSuccessResponse(ipReturn(finalResult));
    }

    /**
     * 将 List<UserIPRedirectionDO> 转化为  List<List<IpRedirectionVO>> 类型数据
     */
    public static List<IpRedirectionVO> ipReturn(List<UserIPRedirectionDO> userIPRedirectionDOList) {
        return userIPRedirectionDOList.stream()
                .map(record -> {
                    IpRedirectionVO vo = new IpRedirectionVO();
                    vo.setRegion(record.getRegion());
                    vo.setLanguageCode(record.getLanguageCode());
                    vo.setCurrencyCode(record.getCurrencyCode());
                    vo.setId(record.getId());
                    return vo;
                })
                .toList();
    }

    /**
     * 构建唯一键：用于识别是否同一条记录
     */
    private String buildKey(UserIPRedirectionDO item) {
        return item.getShopName() + "|" + item.getRegion();
    }

    /**
     * 批量更新ip跳转数据
     */
    public BaseResponse<Object> updateUserIp(String shopName, UserIPRedirectionDO userIPRedirectionDO) {
        // 给每条记录设置 shopName（确保一致）
        userIPRedirectionDO.setShopName(shopName);

        // 两种数据， 一种有id ， 一种没有id，没有id的插入，有id的更新
        boolean updateFlag = userIPRedirectionRepo.updateById(userIPRedirectionDO);

        if (updateFlag) {
            return new BaseResponse<>().CreateSuccessResponse(userIPRedirectionDO);
        }
        return new BaseResponse<>().CreateErrorResponse("update failed");
    }

    /**
     * 根据shopName获取 对应ip跳转数据
     */
    public BaseResponse<Object> selectUserIpList(String shopName) {
        List<UserIPRedirectionDO> userIPRedirectionDOS = userIPRedirectionRepo.selectIpRedirectionByShopName(shopName);
        return new BaseResponse<>().CreateSuccessResponse(ipReturn(userIPRedirectionDOS));
    }


    /**
     * 根据shopName和region获取 对应ip跳转数据
     */
    public BaseResponse<Object> selectUserIpListByShopNameAndRegion(String shopName, String region) {
        List<UserIPRedirectionDO> userIPRedirectionDOS = userIPRedirectionRepo.selectIpRedirectionByShopNameAndRegion(shopName, region);
        return new BaseResponse<>().CreateSuccessResponse(ipReturn(userIPRedirectionDOS).get(0));
    }

    /**
     * 根据shopName获取剩余ip额度
     */
    public BaseResponse<Object> queryUserIpCount(String shopName) {
        // 获取用户计划
        Integer userSubscriptionPlan = iUserSubscriptionsService.getUserSubscriptionPlan(shopName);
        int freeIp = switch (userSubscriptionPlan) {
            case 4 -> 10000;
            case 5 -> 25000;
            case 6 -> 50000;
            default -> 500;
        };

        // 查询已试用额度，
        Long ipCountByShopName = iUserIpService.getIpCountByShopName(shopName);

        // 返回剩余的额度 如果为负数，将额度改为0
        return new BaseResponse<>().CreateSuccessResponse(ipCountByShopName.intValue() > freeIp ? 0 : freeIp - ipCountByShopName.intValue());
    }

    public List<UserIPRedirectionDO> selectAllIpRedirectionByShopName(String shopName) {
        return userIPRedirectionRepo.selectAllIpRedirectionByShopName(shopName);
    }

    public BaseResponse<Object> getWidgetConfigurations(String shopName) {
        WidgetConfigurationsDO data = iWidgetConfigurationsService.getData(shopName);
        if (data == null) {
            return new BaseResponse<>().CreateErrorResponse("query error");
        }

        // 获取ip的跳转数据一块返回
        List<UserIPRedirectionDO> userIPRedirectionDOS = userIPRedirectionRepo.selectIpRedirectionByShopName(shopName);
        WidgetReturnVO widgetReturnVO = new WidgetReturnVO(shopName, data.getLanguageSelector(), data.getCurrencySelector()
                , data.getIpOpen(), data.getIncludedFlag(), data.getFontColor(), data.getBackgroundColor(), data.getButtonColor(), data.getButtonBackgroundColor()
                , data.getOptionBorderColor(), data.getSelectorPosition(), data.getPositionData(), data.getIsTransparent(), userIPRedirectionDOS);

        if (userIPRedirectionDOS != null) {
            return new BaseResponse<>().CreateSuccessResponse(widgetReturnVO);
        } else {
            return new BaseResponse<>().CreateErrorResponse("query error");
        }

    }
}
