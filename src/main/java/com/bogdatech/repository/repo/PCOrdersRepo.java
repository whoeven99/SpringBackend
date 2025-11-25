package com.bogdatech.repository.repo;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bogdatech.repository.entity.PCOrdersDO;
import com.bogdatech.repository.mapper.PCOrdersMapper;
import org.springframework.stereotype.Service;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Service
public class PCOrdersRepo extends ServiceImpl<PCOrdersMapper, PCOrdersDO> {
    public PCOrdersDO getOrderByOrderId(String orderId) {
        return baseMapper.selectOne(new LambdaQueryWrapper<PCOrdersDO>().eq(PCOrdersDO::getOrderId, orderId).eq(PCOrdersDO::getIsDeleted, 0));
    }

    public boolean updateStatusByShopName(Integer id, String status) {
        return baseMapper.update(new LambdaUpdateWrapper<PCOrdersDO>().eq(PCOrdersDO::getId, id).set(PCOrdersDO::getStatus, status)
                .set(PCOrdersDO::getUpdatedAt, Timestamp.from(Instant.now()))) > 0;
    }

    public String getLatestActiveSubscribeId(String shopName) {
        PCOrdersDO pcOrdersDO = baseMapper.selectList(new LambdaQueryWrapper<PCOrdersDO>().eq(PCOrdersDO::getShopName, shopName)
                .eq(PCOrdersDO::getStatus, "ACTIVE").orderByDesc(PCOrdersDO::getCreatedAt)).stream().filter(
                order -> order.getOrderId() != null && order.getOrderId().contains("AppSubscription")
        ).toList().get(0);

        if (pcOrdersDO != null) {
            return pcOrdersDO.getOrderId();
        }
        return null;
    }

    public List<PCOrdersDO> selectOrdersByShopName(String shopName) {
        return baseMapper.selectList(new LambdaQueryWrapper<PCOrdersDO>().eq(PCOrdersDO::getShopName, shopName)
                        .eq(PCOrdersDO::getStatus, "ACTIVE"))
                .stream().filter(data -> data.getShopName() != null && data.getOrderId().contains("AppSubscription")).toList();
    }

    public PCOrdersDO getOrderByShopName(String shopName) {
        return baseMapper.selectOne(new LambdaQueryWrapper<PCOrdersDO>().eq(PCOrdersDO::getShopName, shopName));
    }

    public PCOrdersDO getOrderBySubGid(String orderId) {
        return baseMapper.selectOne(new LambdaQueryWrapper<PCOrdersDO>().eq(PCOrdersDO::getOrderId, orderId));
    }

    public List<PCOrdersDO> selectActiveOrders() {
        return baseMapper.selectList(new LambdaQueryWrapper<PCOrdersDO>().eq(PCOrdersDO::getStatus, "ACTIVE")).stream()
                .filter(data -> data.getShopName() != null && data.getOrderId().contains("AppSubscription")).toList();
    }
}
