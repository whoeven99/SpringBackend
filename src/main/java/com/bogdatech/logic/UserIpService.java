package com.bogdatech.logic;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.bogdatech.Service.ITranslationCounterService;
import com.bogdatech.Service.IUserIpService;
import com.bogdatech.Service.IUserSubscriptionsService;
import com.bogdatech.entity.DO.TranslationCounterDO;
import com.bogdatech.entity.DO.UserIpDO;
import com.bogdatech.entity.VO.IpRedirectionVO;
import com.bogdatech.model.controller.response.BaseResponse;
import com.bogdatech.repository.entity.UserIPRedirectionDO;
import com.bogdatech.repository.repo.UserIPRedirectionRepo;
import com.nimbusds.oauth2.sdk.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    /**
     * 批量存储ip跳转数据
     */
    public BaseResponse<Object> batchAddUserIp(String shopName, List<UserIPRedirectionDO> userIPRedirectionDOList) {
        // 参数校验
        if (CollectionUtils.isEmpty(userIPRedirectionDOList)) {
            return new BaseResponse<>().CreateErrorResponse("userIP list cannot be empty");
        }

        // 给每条记录设置 shopName（确保一致）
        userIPRedirectionDOList.forEach(item -> item.setShopName(shopName));
        List<UserIPRedirectionDO> ipRedirectionList;

        // 批量存储
        userIPRedirectionRepo.saveIpRedirectList(userIPRedirectionDOList);

        // 获取所有的值，过滤存储的值，返回给前端
        ipRedirectionList = userIPRedirectionRepo.selectIpRedirectionByShopName(shopName);
        if (CollectionUtils.isEmpty(ipRedirectionList)) {
            return new BaseResponse<>().CreateErrorResponse("No data found");
        }

        // 将数据库中的记录按唯一键组合为 Map 方便比对
        Map<String, UserIPRedirectionDO> dbRecordMap = ipRedirectionList.stream()
                .collect(Collectors.toMap(
                        this::buildKey,
                        item -> item,
                        (a, b) -> a
                ));

        // 比对提交的数据，筛选本次成功插入的记录（依据唯一键）
        List<UserIPRedirectionDO> savedRecords = userIPRedirectionDOList.stream()
                .map(item -> dbRecordMap.get(buildKey(item)))
                .filter(Objects::nonNull)
                .toList();

        return new BaseResponse<>().CreateSuccessResponse(ipReturn(savedRecords));
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
                    vo.setCurrency(record.getCurrency());
                    vo.setStatus(record.getStatus());
                    vo.setId(record.getId());
                    return vo;
                })
                .toList();
    }

    /**
     * 构建唯一键：用于识别是否同一条记录
     * 必要时你可以加入更多字段
     */
    private String buildKey(UserIPRedirectionDO item) {
        return item.getShopName() + "|" +
                item.getRegion() + "|" +
                item.getLanguageCode() + "|" +
                item.getCurrency();
    }

    /**
     * 批量删除ip跳转数据
     */
    public BaseResponse<Object> batchDeleteUserIp(String shopName, List<Integer> ids) {
        // 参数校验
        if (CollectionUtils.isEmpty(ids)) {
            return new BaseResponse<>().CreateErrorResponse("userIP list cannot be empty");
        }

        // 批量删除ids数据
        Map<Integer, Boolean> integerBooleanMap = userIPRedirectionRepo.deleteIpRedirectList(ids);

        // 搜集integerBooleanMap里面为true的数据返回
        List<Integer> successIds = integerBooleanMap.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).toList();

        if (CollectionUtils.isEmpty(successIds)) {
            return new BaseResponse<>().CreateErrorResponse("No data delete success");
        }

        return new BaseResponse<>().CreateSuccessResponse(successIds);
    }

    /**
     * 批量更新ip跳转数据
     */
    public BaseResponse<Object> updateUserIp(String shopName, UserIPRedirectionDO userIPRedirectionDO) {
        // 给每条记录设置 shopName（确保一致）
        userIPRedirectionDO.setShopName(shopName);

        // 两种数据， 一种有id ， 一种没有id，没有id的插入，有id的更新
        userIPRedirectionRepo.saveOrUpdate(userIPRedirectionDO);

        // 获取对应数据
        UserIPRedirectionDO ipRedirectionByShopNameAndRegion = userIPRedirectionRepo.getIpRedirectionByShopNameAndRegion(shopName, userIPRedirectionDO.getRegion(),
                userIPRedirectionDO.getLanguageCode(), userIPRedirectionDO.getCurrency());

        if (ipRedirectionByShopNameAndRegion == null) {
            return new BaseResponse<>().CreateErrorResponse("No data found");
        }
        return new BaseResponse<>().CreateSuccessResponse(ipRedirectionByShopNameAndRegion);
    }


    public BaseResponse<Object> updateUserIpStatus(Integer id, Boolean status) {
        // 校验参数
        if (id == null || status == null) {
            return new BaseResponse<>().CreateErrorResponse("shopName or id or status cannot be empty");
        }

        // 更新状态
        boolean statusFlag = userIPRedirectionRepo.updateIpRedirectStatus(id, status);
        if (statusFlag) {
            return new BaseResponse<>().CreateSuccessResponse(id);
        }
        return new BaseResponse<>().CreateErrorResponse("status update error");
    }

    /**
     * 根据shopName获取 对应ip跳转数据
     */
    public BaseResponse<Object> selectUserIpList(String shopName) {
        List<UserIPRedirectionDO> userIPRedirectionDOS = userIPRedirectionRepo.selectIpRedirectionByShopName(shopName);
        return new BaseResponse<>().CreateSuccessResponse(ipReturn(userIPRedirectionDOS));
    }


    public BaseResponse<Object> selectUserIpListByShopNameAndRegion(String shopName, String region) {
        List<UserIPRedirectionDO> userIPRedirectionDOS = userIPRedirectionRepo.selectIpRedirectionByShopNameAndRegion(shopName, region);
        return new BaseResponse<>().CreateSuccessResponse(ipReturn(userIPRedirectionDOS));
    }
}
