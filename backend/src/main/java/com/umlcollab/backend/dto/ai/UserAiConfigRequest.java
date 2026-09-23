package com.umlcollab.backend.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAiConfigRequest {
    private String provider;
    private String apiKey;
    private String model;
    private String baseUrl;
    private Boolean customEnabled;
    private Boolean clearApiKey;
}
