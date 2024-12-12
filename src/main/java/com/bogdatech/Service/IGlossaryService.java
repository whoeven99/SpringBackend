package com.bogdatech.Service;

import com.bogdatech.entity.GlossaryDO;

public interface IGlossaryService {
    Boolean insertGlossaryInfo(GlossaryDO glossaryDO);

    boolean deleteGlossaryById(GlossaryDO glossaryDO);

    Object getGlossaryByShopName(GlossaryDO glossaryDO);

    boolean updateGlossaryInfoById(GlossaryDO glossaryDO);
}
