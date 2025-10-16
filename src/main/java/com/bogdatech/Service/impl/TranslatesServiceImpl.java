package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.ITranslatesService;
import com.bogdatech.entity.DO.TranslatesDO;
import com.bogdatech.mapper.TranslatesMapper;
import com.bogdatech.model.controller.request.AutoTranslateRequest;
import com.bogdatech.model.controller.request.ShopifyRequest;
import com.bogdatech.model.controller.request.TranslateRequest;
import com.bogdatech.model.controller.response.BaseResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.bogdatech.constants.TranslateConstants.SHOP_NAME;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

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
    public int updateTranslateStatus(String shopName, int status, String target, String source, String accessToken) {
        return baseMapper.updateTranslateStatus(status, shopName, target, source, accessToken);
    }

    @Override
    public List<TranslatesDO> readInfoByShopName(String shopName, String source) {
        return baseMapper.selectList(new QueryWrapper<TranslatesDO>().eq("shop_name", shopName).eq("source", source));
    }

    @Override
    public List<Integer> readStatusInTranslatesByShopName(String shopName) {
        return baseMapper.readStatusInTranslatesByShopName(shopName);
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
    public List<TranslatesDO> getLanguageListCounter(String shopName) {
        return baseMapper.getLanguageListCounter(shopName);
    }

    @Override
    public void updateTranslatesResourceType(String shopName, String target, String source, String resourceType) {
        baseMapper.updateTranslatesResourceType(shopName, target, source, resourceType);
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
    public TranslatesDO selectLatestOne(TranslateRequest request) {
        QueryWrapper<TranslatesDO> wrapper = new QueryWrapper<>();
        wrapper.select(
                "TOP 1 id, source, access_token, target, shop_name, status, resource_type"
        );
        // shopName 是参数
        wrapper.eq(SHOP_NAME, request.getShopName());
        // source 是参数
        wrapper.eq("source", request.getSource());
        wrapper.orderByDesc("update_at");

        return baseMapper.selectOne(wrapper);
    }

    @Override
    public String getResourceTypeByshopNameAndTargetAndSource(String shopName, String target, String source) {
        return baseMapper.getResourceTypeByshopNameAndTarget(shopName, target, source);
    }

    @Override
    public void updateStatus3To6(String shopName) {
        TranslatesDO translatesDO = new TranslatesDO();
        translatesDO.setStatus(6);
        final int maxRetries = 3;
        int attempt = 0;
        boolean success = false;
        while (attempt < maxRetries && !success) {
            attempt++;
            try {
                //将所有状态3都改为6
                int affectedRows = baseMapper.update(new LambdaUpdateWrapper<TranslatesDO>()
                        .eq(TranslatesDO::getShopName, shopName)
                        .eq(TranslatesDO::getStatus, 3)
                        .set(TranslatesDO::getStatus, 6)
                );
                if (affectedRows > 0) {
                    success = true;
                }
            } catch (Exception e) {
                appInsights.trackException(e);
            }
        }
    }

    @Override
    public List<TranslatesDO> getStatus2Data() {
        return baseMapper.selectList(new QueryWrapper<TranslatesDO>().eq("status", 2));
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
            return new BaseResponse<>().CreateSuccessResponse(new AutoTranslateRequest(shopName, source, target, autoTranslate));
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
    public void insertShopTranslateInfoByShopify(ShopifyRequest shopifyRequest, String locale, String source) {
        //获取shopify店铺信息
        TranslatesDO translatesDO = baseMapper.selectOne(new QueryWrapper<TranslatesDO>().eq("shop_name", shopifyRequest.getShopName()).eq("source", source).eq("target", locale));
        if (translatesDO == null) {
            //插入这条数据
            baseMapper.insertShopTranslateInfo(source, shopifyRequest.getAccessToken(), locale, shopifyRequest.getShopName(), 0);
        }
    }

    @Override
    public void updateStopStatus(String shopName, String source) {
        baseMapper.update(new LambdaUpdateWrapper<TranslatesDO>()
                .eq(TranslatesDO::getShopName, shopName)
                .eq(TranslatesDO::getSource, source)
                .eq(TranslatesDO::getStatus, 2)
                .set(TranslatesDO::getStatus, 7)
        );

    }

    @Override
    public List<TranslatesDO> listTranslatesDOByShopName(String shopName) {
        return baseMapper.selectList(new LambdaQueryWrapper<TranslatesDO>().eq(TranslatesDO::getShopName, shopName));
    }

    @Override
    public TranslatesDO getSingleTranslateDO(String shopName, String source, String target) {
        return baseMapper.selectOne(new LambdaQueryWrapper<TranslatesDO>().eq(TranslatesDO::getShopName, shopName).eq(TranslatesDO::getSource, source).eq(TranslatesDO::getTarget, target));
    }
}
