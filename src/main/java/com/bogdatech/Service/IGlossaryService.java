package com.bogdatech.Service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bogdatech.entity.DO.GlossaryDO;

public interface IGlossaryService extends IService<GlossaryDO> {
    Boolean insertGlossaryInfo(GlossaryDO glossaryDO);

    boolean deleteGlossaryById(GlossaryDO glossaryDO);

    GlossaryDO[] getGlossaryByShopName(String shopName);

    boolean updateGlossaryInfoById(GlossaryDO glossaryDO);

    GlossaryDO getSingleGlossaryByShopNameAndSource(String shopName, String sourceText, String rangeCode);

    boolean updateGlossaryStatusByShopName(String shopName, int status);
}
