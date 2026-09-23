package com.umlcollab.backend.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAiConfigDto {
    private String provider;
    private String model;
    private String baseUrl;
    private boolean hasCustomApiKey;
    private String maskedApiKey;
    private boolean customEnabled;
    private boolean configured;
    private String effectiveProvider;
    private String effectiveModel;
    private boolean systemDefaultAvailable;
}
