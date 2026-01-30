package com.bogda.service.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.service.Service.ITranslatesService;
import com.bogda.common.entity.DO.TranslatesDO;
import com.bogda.service.mapper.TranslatesMapper;
import com.bogda.common.controller.request.AutoTranslateRequest;
import com.bogda.common.controller.request.TranslateRequest;
import com.bogda.common.controller.response.BaseResponse;
import com.bogda.common.utils.AppInsightsUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class TranslatesServiceImpl extends ServiceImpl<TranslatesMapper, TranslatesDO> implements ITranslatesService {


    @Override
    public Integer readStatus(TranslateRequest request) {
        return baseMapper.getStatusInTranslates(request.getShopName(), request.getTarget(), request.getSource());
    }

    @Override
    public Integer insertShopTranslateInfo(TranslateRequest request, int status) {
        return baseMapper.insertShopTranslateInfo(request.getSource(), request.getAccessToken(),
                request.getTarget(), request.getShopName(), status);
    }

    @Override
    public List<TranslatesDO> readTranslateInfo(Integer status) {
        return baseMapper.readTranslateInfo(status);
    }

    @Override
    public int updateTranslateStatus(String shopName, int status, String target, String source) {
        return baseMapper.update(new LambdaUpdateWrapper<TranslatesDO>().eq(TranslatesDO::getShopName, shopName)
                .eq(TranslatesDO::getTarget, target)
                .eq(TranslatesDO::getSource, source)
                .set(TranslatesDO::getStatus, status));
    }

    @Override
    public List<TranslatesDO> readInfoByShopName(String shopName, String source) {
        return baseMapper.selectList(new QueryWrapper<TranslatesDO>().eq("shop_name", shopName).eq("source", source));
    }

    @Override
    public TranslatesDO readTranslateDOByArray(TranslatesDO translatesDO) {
        return baseMapper.readTranslatesDOByArray(translatesDO.getShopName(), translatesDO.getSource(), translatesDO.getTarget());
    }

    @Override
    public int updateStatusByShopNameAnd2(String shopName) {
        return baseMapper.updateStatusByShopNameAnd2(shopName);
    }

    @Override
    public String getShopName(String shopName, String target, String source) {
        return baseMapper.getShopName(shopName, target, source);
    }

    @Override
    public Boolean deleteFromTranslates(TranslateRequest request) {
        return baseMapper.deleteFromTranslates(request.getShopName(), request.getSource(), request.getTarget());
    }

    @Override
    public Integer getStatusByShopNameAndTargetAndSource(String shopName, String target, String source) {
        return baseMapper.getStatusByShopNameAndTargetAndSource(shopName, target, source);
    }

    @Override
    public Integer getIdByShopNameAndTargetAndSource(String shopName, String target, String source) {
        return baseMapper.getIdByShopNameAndTarget(shopName, target, source);
    }

    @Override
    public boolean updateStatus3To6(String shopName) {
        final int maxRetries = 3;
        int attempt = 0;

        while (attempt < maxRetries) {
            attempt++;
            try {
                int affectedRows = baseMapper.update(
                        new LambdaUpdateWrapper<TranslatesDO>()
                                .eq(TranslatesDO::getShopName, shopName)
                                .eq(TranslatesDO::getStatus, 3)
                                .set(TranslatesDO::getStatus, 6)
                );

                AppInsightsUtils.trackTrace("updateStatus3To6: " + shopName + " 修改行数：" + affectedRows);

                // 正常结束，无需再重试
                return true;
            } catch (Exception e) {
                AppInsightsUtils.trackException(e);
                if (attempt >= maxRetries) {
                    AppInsightsUtils.trackTrace("FatalException updateStatus3To6: " + shopName + " 最终失败");
                }
            }
        }
        return false;
    }

    @Override
    public BaseResponse<Object> updateAutoTranslateByShopName(String shopName, Boolean autoTranslate, String source, String target) {
        int flag = baseMapper.update(new UpdateWrapper<TranslatesDO>()
                .eq("shop_name", shopName)
                .eq("source", source)
                .eq("target", target)
                .set("auto_translate", autoTranslate));

        //将source不为修改的值的True改为false
        baseMapper.update(new UpdateWrapper<TranslatesDO>()
                .eq("shop_name", shopName)
                .ne("source", source)
                .set("auto_translate", false));
        if (flag > 0) {
            return new BaseResponse<>().CreateSuccessResponse(new AutoTranslateRequest(shopName, source, target, autoTranslate, null));
        } else {
            return new BaseResponse<>().CreateErrorResponse("更新失败");
        }

    }

    @Override
    public List<TranslatesDO> readAllTranslates() {
        //获取所有auto_translate为true，按shop_name降序排序
        return baseMapper.selectList(new LambdaQueryWrapper<TranslatesDO>().eq(TranslatesDO::getAutoTranslate, true).orderByDesc(TranslatesDO::getShopName));
    }


    @Override
    public void updateAutoTranslateByShopNameToFalse(String shopName) {
        baseMapper.update(new UpdateWrapper<TranslatesDO>().eq("shop_name", shopName).set("auto_translate", false));
    }

    @Override
    public void insertLanguageStatus(TranslateRequest request) {
        Integer status = readStatus(request);
        if (status == null) {
            insertShopTranslateInfo(request, 0);
        }
    }

    @Override
    public void updateAllStatusTo0(String shopName) {
        if (shopName != null) {
            baseMapper.update(new UpdateWrapper<TranslatesDO>().eq("shop_name", shopName).set("status", 0));
        }
    }

    @Override
    public boolean insertShopTranslateInfoByShopify(String shopName, String accessToken, String locale, String source) {
        return baseMapper.insertShopTranslateInfo(source, accessToken, locale, shopName, 0) > 0;
    }

    @Override
    public List<TranslatesDO> listTranslatesDOByShopName(String shopName) {
        return baseMapper.selectList(new LambdaQueryWrapper<TranslatesDO>().eq(TranslatesDO::getShopName, shopName));
    }

    @Override
    public void updateAutoTranslateByShopNameAndTargetToFalse(String shopName, String target) {
        baseMapper.update(new LambdaUpdateWrapper<TranslatesDO>().eq(TranslatesDO::getShopName, shopName).eq(TranslatesDO::getTarget, target).set(TranslatesDO::getAutoTranslate, false));
    }

    @Override
    public List<String> selectTargetByShopName(String shopName) {
        List<TranslatesDO> doList = baseMapper.selectList(new LambdaQueryWrapper<TranslatesDO>().select(TranslatesDO::getTarget).eq(TranslatesDO::getShopName, shopName));
        if (doList.isEmpty()){
            return Collections.emptyList();
        }
        return doList.stream().map(TranslatesDO::getTarget).collect(Collectors.toList());
    }

    @Override
    public List<TranslatesDO> selectTargetByShopNameSource(String shopName, String source) {
        return baseMapper.selectList(new LambdaQueryWrapper<TranslatesDO>().eq(TranslatesDO::getShopName, shopName).eq(TranslatesDO::getSource, source));
    }

    @Override
    public List<TranslatesDO> listAutoTranslates(String shopName) {
        return baseMapper.selectList(new LambdaQueryWrapper<TranslatesDO>().eq(TranslatesDO::getShopName, shopName).eq(TranslatesDO::getAutoTranslate, true));
    }
}
