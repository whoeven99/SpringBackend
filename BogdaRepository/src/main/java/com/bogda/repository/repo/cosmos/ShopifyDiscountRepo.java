package com.bogda.repository.repo.cosmos;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.*;
import com.azure.cosmos.util.CosmosPagedIterable;
import com.azure.cosmos.models.CosmosPatchItemRequestOptions;
import com.bogda.common.utils.AppInsightsUtils;
import com.bogda.repository.container.ShopifyDiscountDO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class ShopifyDiscountRepo {
    @Autowired
    private CosmosContainer discountContainer;

    /**
     * 保存折扣类数据
     */
    public boolean saveDiscount(ShopifyDiscountDO data) {
        try {
            data.setId(data.getDiscountGid());
            data.setCreatedAt(String.valueOf(Instant.now()));
            data.setUpdatedAt(String.valueOf(Instant.now()));
            CosmosItemRequestOptions options = new CosmosItemRequestOptions().setContentResponseOnWriteEnabled(false);
            discountContainer.upsertItem(data, options);
            return true;
        } catch (Exception e) {
            AppInsightsUtils.trackTrace("FatalException saveDiscount 保存discount数据失败 " + " id: " + data.getDiscountGid()
                    + " shopName: " + data.getShopName() + " 原因： " + e);
            return false;
        }
    }

    /**
     * 查询折扣类数据
     *
     * @param id 存的是discountGid
     */
    public ShopifyDiscountDO getDiscountByIdAndShopName(String id, String shopName) {
        try {
            return discountContainer.readItem(id, new PartitionKey(shopName), ShopifyDiscountDO.class).getItem();
        } catch (Exception e) {
            AppInsightsUtils.trackTrace("FatalException findById 查询discount数据失败 " + " id: " + id + " shopName: " + shopName + " 原因： " + e);
        }
        return null;
    }

    /**
     * 删除折扣类数据
     */
    public boolean deleteByIdAndShopName(String id, String shopName) {
        try {
            CosmosItemRequestOptions options = new CosmosItemRequestOptions().setContentResponseOnWriteEnabled(false);
            discountContainer.deleteItem(id, new PartitionKey(shopName), options);
        } catch (Exception e) {
            AppInsightsUtils.trackTrace("FatalException deleteByIdAndShopName 删除discount数据失败 " + " id: "
                    + id + " shopName: " + shopName + " 原因： " + e);
            return false;
        }
        return true;
    }

    /**
     * 局部修改折扣类数据
     */
    public void updateStatus(String id, String shopName, Boolean status, ShopifyDiscountDO.DiscountData data) {
        CosmosPatchOperations patchOperations = CosmosPatchOperations.create()
                .replace("/status", status)
                .replace("/updatedAt", String.valueOf(Instant.now()));

        discountContainer.patchItem(
                id,
                new PartitionKey(shopName),
                patchOperations,
                ShopifyDiscountDO.class
        );
    }

    /**
     * 批量查询cosmos数据
     */
    public <T> List<T> queryBySql(String sql, List<SqlParameter> parameters, String partitionKey, Class<T> resultClass) {

        try {
            SqlQuerySpec querySpec = new SqlQuerySpec(sql, parameters);

            CosmosQueryRequestOptions options = new CosmosQueryRequestOptions()
                            .setPartitionKey(new PartitionKey(partitionKey))
                            .setMaxDegreeOfParallelism(1);

            List<T> result = new ArrayList<>();

            CosmosPagedIterable<T> ts = discountContainer.queryItems(querySpec, options, resultClass);
            ts.forEach(result::add);
            return result;
        } catch (Exception e) {
            AppInsightsUtils.trackTrace("FatalException queryBySql 查询discount数据失败 " + " sql: " + sql +
                    " partitionKey: " + partitionKey + " 原因： " + e);
            return null;
        }
    }

    public boolean updateDiscount(String id, String shopName, ShopifyDiscountDO.DiscountData newDiscountData) {
        try {
            CosmosPatchOperations patchOps = CosmosPatchOperations.create().replace("/discountData", newDiscountData)
                    .replace("/updatedAt", String.valueOf(Instant.now()));
            discountContainer.patchItem(id, new PartitionKey(shopName), patchOps, Objects.class);
            return true;

        } catch (Exception e) {
            AppInsightsUtils.trackTrace("FatalException updateDiscount 更新discount数据失败 " + " id: " + id + " shopName: " + shopName + " 原因： " + e);
           return false;
        }
    }
}
