package com.bogda.api.logic.agent;

import lombok.Data;

@Data
public class Feedback {
    private Long adviceId;
    private String shopName;
    private String feedbackType;
    private Integer rating;
    private String notes;
}
