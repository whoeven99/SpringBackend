package com.bogda.api.entity.VO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class KeywordVO {
    public String keyword;
    public String translation;
    public boolean caseSensitive;

}
