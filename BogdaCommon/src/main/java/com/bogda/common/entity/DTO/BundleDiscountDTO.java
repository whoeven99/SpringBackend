package com.bogda.common.entity.DTO;

import com.bogda.common.entity.VO.BundleDisplayDataVO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BundleDiscountDTO {
    private List<BundleDisplayDataVO> myOffers;
}
