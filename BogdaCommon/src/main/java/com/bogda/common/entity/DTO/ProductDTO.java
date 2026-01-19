package com.bogda.common.entity.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProductDTO {
    private String id;
    private String productDescription;
    private String imageUrl;
    private String imageAltText;
    private String productType;
    private String productTitle;
}
