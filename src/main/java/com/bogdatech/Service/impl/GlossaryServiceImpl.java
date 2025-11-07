package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.IGlossaryService;
import com.bogdatech.entity.DO.GlossaryDO;
import com.bogdatech.mapper.GlossaryMapper;
import org.springframework.stereotype.Service;

@Service
public class GlossaryServiceImpl extends ServiceImpl<GlossaryMapper, GlossaryDO> implements IGlossaryService {
    @Override
    public Boolean insertGlossaryInfo(GlossaryDO glossaryDO) {
        return baseMapper.insertGlossaryInfo(glossaryDO.getShopName(), glossaryDO.getSourceText(), glossaryDO.getTargetText(), glossaryDO.getRangeCode(), glossaryDO.getCaseSensitive(), glossaryDO.getStatus()) > 0;
    }

    @Override
    public boolean deleteGlossaryById(GlossaryDO glossaryDO) {
        return baseMapper.deleteById(glossaryDO.getId()) > 0;
    }

    @Override
    public GlossaryDO[] getGlossaryByShopName(String shopName) {
        return baseMapper.readGlossaryByShopName(shopName);
    }

    @Override
    public boolean updateGlossaryInfoById(GlossaryDO glossaryDO) {
        return baseMapper.updateGlossaryInfoById(glossaryDO.getId(), glossaryDO.getSourceText(), glossaryDO.getTargetText(), glossaryDO.getRangeCode(), glossaryDO.getCaseSensitive(), glossaryDO.getStatus()) > 0;
    }

    @Override
    public GlossaryDO getSingleGlossaryByShopNameAndSource(String shopName, String sourceText, String rangeCode) {
        return baseMapper.getSingleGlossaryByShopNameAndSource(shopName, sourceText, rangeCode);
    }

    @Override
    public boolean updateGlossaryStatusByShopName(String shopName, int status) {
        return baseMapper.update(new LambdaUpdateWrapper<GlossaryDO>().eq(GlossaryDO::getShopName, shopName)
                .set(GlossaryDO::getStatus, status)) > 0;
    }
}
