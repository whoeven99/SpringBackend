package com.bogdatech.model.controller.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TypeSplitResponse {
    private StringBuilder before;
    private StringBuilder after;
}
