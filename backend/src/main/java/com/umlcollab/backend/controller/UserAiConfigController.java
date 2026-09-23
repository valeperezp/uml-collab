package com.umlcollab.backend.controller;

import com.umlcollab.backend.dto.ai.AiTestRequest;
import com.umlcollab.backend.dto.ai.AiTestResponse;
import com.umlcollab.backend.dto.ai.UserAiConfigDto;
import com.umlcollab.backend.dto.ai.UserAiConfigRequest;
import com.umlcollab.backend.security.CurrentUser;
import com.umlcollab.backend.service.UserAiConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user/ai-config")
public class UserAiConfigController {

    private final UserAiConfigService configService;

    public UserAiConfigController(UserAiConfigService configService) {
        this.configService = configService;
    }

    @GetMapping
    public ResponseEntity<UserAiConfigDto> getConfig() {
        return ResponseEntity.ok(configService.getUserConfig(CurrentUser.id()));
    }

    @PutMapping
    public ResponseEntity<UserAiConfigDto> updateConfig(@RequestBody UserAiConfigRequest request) {
        return ResponseEntity.ok(configService.updateUserConfig(CurrentUser.id(), request));
    }

    @DeleteMapping
    public ResponseEntity<UserAiConfigDto> resetConfig() {
        return ResponseEntity.ok(configService.resetUserConfig(CurrentUser.id()));
    }

    @PostMapping("/test")
    public ResponseEntity<AiTestResponse> testConfig(@RequestBody AiTestRequest request) {
        return ResponseEntity.ok(configService.testConfig(CurrentUser.id(), request));
    }
}
