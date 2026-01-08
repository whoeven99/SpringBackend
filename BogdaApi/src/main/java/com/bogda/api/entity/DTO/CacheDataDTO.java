package com.bogda.api.entity.DTO;

import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
public class CacheDataDTO {
    private String text;
    private long count;

    public CacheDataDTO(String text, long count) {
        this.text = text;
        this.count = count;
    }
}
