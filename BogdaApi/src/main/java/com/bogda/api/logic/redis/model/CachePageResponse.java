package com.bogda.api.logic.redis.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class CachePageResponse<T> {
    private int pageNo;
    private int pageSize;
    private long total;
    private List<T> list;

    public CachePageResponse(int pageNo, int pageSize, long total, List<T> list) {
        this.pageNo = pageNo;
        this.pageSize = pageSize;
        this.total = total;
        this.list = list;
    }
}
