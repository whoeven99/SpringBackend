package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.ITranslatesService;
import com.bogdatech.entity.TranslatesDO;
import com.bogdatech.mapper.TranslatesMapper;
import com.bogdatech.model.controller.request.AutoTranslateRequest;
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
        wrapper.eq(SHOP_NAME, request.getShopName()); // shopName 是参数
        wrapper.eq("source", request.getSource());       // source 是参数
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
                QueryWrapper<TranslatesDO> wrapper = new QueryWrapper<>();
                wrapper.select(
                        "TOP 1 source, access_token, target, shop_name, status, resource_type"
                );
                wrapper.eq(SHOP_NAME, shopName); // shopName 是参数
                wrapper.orderByDesc("update_at");
                TranslatesDO getOneTranslate = baseMapper.selectOne(wrapper);
                getOneTranslate.setStatus(6);
                int affectedRows = baseMapper.update(getOneTranslate, new UpdateWrapper<TranslatesDO>()
                        .eq(SHOP_NAME, shopName)
                        .eq("status", 3)
                        .eq("source", getOneTranslate.getSource())
                        .eq("target", getOneTranslate.getTarget())
                );
                if (affectedRows > 0) {
                    success = true;
                }
            } catch (Exception e) {
                appInsights.trackTrace("Exception during update attempt "
                        + attempt + "errorMessage: "
                        + e.getMessage()
                        + " error: " + e);
            }
        }
    }

    @Override
    public List<TranslatesDO> getStatus2Data() {
        return baseMapper.selectList(new QueryWrapper<TranslatesDO>().eq("status", 2));
    }

    @Override
    public BaseResponse<Object> updateAutoTranslateByShopName(String shopName, Boolean autoTranslate, String source, String target) {
        //一个用户只允许一条定时任务
        //先判断该用户是否将其他翻译项设为定时任务
        List<TranslatesDO> isTrueList = baseMapper.selectList(new QueryWrapper<TranslatesDO>().eq("shop_name", shopName).eq("auto_translate", true));
        if (!isTrueList.isEmpty()) {
            //将list集合里面的值全部修改为false
            for (TranslatesDO translatesDO : isTrueList) {
                if (translatesDO.getAutoTranslate()) {
                    baseMapper.update(new UpdateWrapper<TranslatesDO>().eq("id", translatesDO.getId()).set("auto_translate", false));
                }
            }
            if (!autoTranslate){
                return new BaseResponse<>().CreateSuccessResponse(new AutoTranslateRequest(shopName, source, target, autoTranslate));
            }
        }

        int flag = baseMapper.update(new UpdateWrapper<TranslatesDO>()
                .eq("shop_name", shopName)
                .eq("source", source)
                .eq("target", target)
                .set("auto_translate", autoTranslate));
        if (flag > 0) {
            return new BaseResponse<>().CreateSuccessResponse(new AutoTranslateRequest(shopName, source, target, autoTranslate));
        } else {
            return new BaseResponse<>().CreateErrorResponse("更新失败");
        }


    }

    @Override
    public List<TranslatesDO> readAllTranslates() {
        return baseMapper.selectList(new QueryWrapper<TranslatesDO>().eq("auto_translate", true));
    }

}
