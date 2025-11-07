package com.bogdatech.Service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.Service.IPCUserPicturesService;
import com.bogdatech.entity.DO.PCUserPicturesDO;
import com.bogdatech.mapper.PCUserPicturesMapper;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class PCUserPicturesServiceImpl extends ServiceImpl<PCUserPicturesMapper, PCUserPicturesDO> implements IPCUserPicturesService {
    @Override
    public boolean insertPictureData(PCUserPicturesDO pcUserPicturesDO) {
        //判断数据库中是否有该数据
        PCUserPicturesDO userPicture = baseMapper.selectOne(new LambdaQueryWrapper<PCUserPicturesDO>().eq(PCUserPicturesDO::getShopName, pcUserPicturesDO.getShopName()).eq(PCUserPicturesDO::getImageId, pcUserPicturesDO.getImageId()).eq(PCUserPicturesDO::getImageBeforeUrl, pcUserPicturesDO.getImageBeforeUrl()).eq(PCUserPicturesDO::getLanguageCode, pcUserPicturesDO.getLanguageCode()).eq(PCUserPicturesDO::getIsDeleted, 0));
        if (userPicture != null) {
            //有则更新
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            pcUserPicturesDO.setUpdateAt(now);
            return baseMapper.update(pcUserPicturesDO, new LambdaUpdateWrapper<PCUserPicturesDO>().eq(PCUserPicturesDO::getShopName, pcUserPicturesDO.getShopName()).eq(PCUserPicturesDO::getImageId, pcUserPicturesDO.getImageId()).eq(PCUserPicturesDO::getImageBeforeUrl, pcUserPicturesDO.getImageBeforeUrl()).eq(PCUserPicturesDO::getLanguageCode, pcUserPicturesDO.getLanguageCode()).eq(PCUserPicturesDO::getIsDeleted, false)) > 0;
        } else {
            //没有则插入
            pcUserPicturesDO.setId(null);
            pcUserPicturesDO.setIsDeleted(0);
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            pcUserPicturesDO.setCreateAt(now);
            pcUserPicturesDO.setUpdateAt(now);
            return baseMapper.insert(pcUserPicturesDO) > 0;
        }
    }

    @Override
    public boolean deletePictureData(String shopName, String imageId, String imageBeforeUrl, String languageCode) {
        return baseMapper.update(new LambdaUpdateWrapper<PCUserPicturesDO>().eq(PCUserPicturesDO::getShopName, shopName).eq(PCUserPicturesDO::getImageId, imageId).eq(PCUserPicturesDO::getImageBeforeUrl, imageBeforeUrl).eq(PCUserPicturesDO::getLanguageCode, languageCode).set(PCUserPicturesDO::getIsDeleted, 1)) > 0;
    }

    @Override
    public List<PCUserPicturesDO> listPcUserPics(String shopName, String imageId) {
        return baseMapper.selectList(new LambdaQueryWrapper<PCUserPicturesDO>()
                .eq(PCUserPicturesDO::getShopName, shopName)
                .eq(PCUserPicturesDO::getImageId, imageId)
                .eq(PCUserPicturesDO::getIsDeleted, 0));
    }

    @Override
    public boolean updatePictureData(String shopName, PCUserPicturesDO pcUserPicturesDO) {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        pcUserPicturesDO.setUpdateAt(now);
        return baseMapper.update(pcUserPicturesDO, new LambdaUpdateWrapper<PCUserPicturesDO>().eq(PCUserPicturesDO::getShopName, shopName).eq(PCUserPicturesDO::getImageId, pcUserPicturesDO.getImageId()).eq(PCUserPicturesDO::getLanguageCode, pcUserPicturesDO.getLanguageCode())) > 0;
    }

    @Override
    public List<PCUserPicturesDO> getUserPicByShopNameAndImageIdAndLanguageCode(String shopName, String imageId, String languageCode) {
        return baseMapper.selectList(new LambdaQueryWrapper<PCUserPicturesDO>().eq(PCUserPicturesDO::getShopName, shopName).eq(PCUserPicturesDO::getImageId, imageId).eq(PCUserPicturesDO::getLanguageCode, languageCode).eq(PCUserPicturesDO::getIsDeleted, 0));
    }

    @Override
    public boolean updatePictureAfterUrl(String shopName, String imageId, String imageBeforeUrl, String languageCode) {
        return baseMapper.update(new LambdaUpdateWrapper<PCUserPicturesDO>().eq(PCUserPicturesDO::getShopName, shopName)
                .eq(PCUserPicturesDO::getImageId, imageId).eq(PCUserPicturesDO::getImageBeforeUrl, imageBeforeUrl)
                .eq(PCUserPicturesDO::getLanguageCode, languageCode).eq(PCUserPicturesDO::getIsDeleted, 0)
                .set(PCUserPicturesDO::getImageAfterUrl, null)) > 0;
    }
}
