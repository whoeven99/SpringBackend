package com.bogda.api.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogda.api.Service.IGlossaryService;
import com.bogda.api.entity.DO.GlossaryDO;
import com.bogda.api.mapper.GlossaryMapper;
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
        return baseMapper.selectOne(new LambdaQueryWrapper<GlossaryDO>().eq(GlossaryDO::getShopName, shopName)
                .eq(GlossaryDO::getRangeCode, rangeCode)
                .apply("HASHBYTES('SHA2_256', CONVERT(VARBINARY(256), source_text)) = HASHBYTES('SHA2_256', CONVERT(VARBINARY(256), {0}))", sourceText));
    }
}
