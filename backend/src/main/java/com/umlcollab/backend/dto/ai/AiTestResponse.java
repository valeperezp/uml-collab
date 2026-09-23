package com.umlcollab.backend.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiTestResponse {
    private boolean success;
    private String message;
    private Long latencyMs;
    private String provider;
    private String model;
}
