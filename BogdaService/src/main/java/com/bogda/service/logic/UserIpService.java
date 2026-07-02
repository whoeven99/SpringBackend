package com.bogda.service.logic;

import com.bogda.common.entity.VO.IpRedirectionVO;
import com.bogda.common.controller.response.BaseResponse;
import com.bogda.repository.entity.UserIPRedirectionDO;
import com.bogda.repository.repo.UserIPRedirectionRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;


@Service
public class UserIpService {
    @Autowired
    private UserIPRedirectionRepo userIPRedirectionRepo;

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
     * 将 List<UserIPRedirectionDO> 转化为 List<IpRedirectionVO>
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

    public List<UserIPRedirectionDO> selectAllIpRedirectionByShopName(String shopName) {
        return userIPRedirectionRepo.selectAllIpRedirectionByShopName(shopName);
    }
}
