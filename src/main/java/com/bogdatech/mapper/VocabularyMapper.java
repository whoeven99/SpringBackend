package com.bogdatech.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bogdatech.entity.VocabularyDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface VocabularyMapper extends BaseMapper<VocabularyDO> {
    // 批量插入
    @Insert({
            "INSERT INTO Vocabulary (en, es, fr, de, pt_BR, pt_PT, zh_CN, zh_TW, ja, it, ru, ko, nl, da, hi, bg, cs, el, fi, hr, hu, id, lt, nb, pl, ro, sk, sl, sv, th, tr, vi, ar, no, uk, lv, et) VALUES (#{item.en}, #{item.es}, #{item.fr}, #{item.de}, #{item.ptBR}, #{item.ptPT}, #{item.zhCN}, #{item.zhTW}, #{item.ja}, #{item.it}, #{item.ru}, #{item.ko}, #{item.nl}, #{item.da}, #{item.hi}, #{item.bg}, #{item.cs}, #{item.el}, #{item.fi}, #{item.hr}, #{item.hu}, #{item.id}, #{item.lt}, #{item.nb}, #{item.pl}, #{item.ro}, #{item.sk}, #{item.sl}, #{item.sv}, #{item.th}, #{item.tr}, #{item.vi}, #{item.ar}, #{item.no}, #{item.uk}, #{item.lv}, #{item.et})"
    })
    void insertSingle(@Param("item") VocabularyDO item);


}
