package com.umlcollab.backend.service;

import com.umlcollab.backend.ai.AiClient;
import com.umlcollab.backend.ai.AiConfig;
import com.umlcollab.backend.dto.ai.AiTestRequest;
import com.umlcollab.backend.dto.ai.AiTestResponse;
import com.umlcollab.backend.dto.ai.UserAiConfigDto;
import com.umlcollab.backend.dto.ai.UserAiConfigRequest;
import com.umlcollab.backend.exception.NotFoundException;
import com.umlcollab.backend.model.User;
import com.umlcollab.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserAiConfigService {

    private final UserRepository userRepository;
    private final AiClient aiClient;

    public UserAiConfigService(UserRepository userRepository, AiClient aiClient) {
        this.userRepository = userRepository;
        this.aiClient = aiClient;
    }

    public UserAiConfigDto getUserConfig(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));

        AiConfig resolved = aiClient.resolveConfigForUser(user);
        boolean hasCustomKey = user.getAiApiKey() != null && !user.getAiApiKey().isBlank();
        String maskedKey = hasCustomKey ? maskKey(user.getAiApiKey()) : null;

        return UserAiConfigDto.builder()
                .provider(user.getAiProvider() != null ? user.getAiProvider() : aiClient.getDefaultConfig().getProvider())
                .model(user.getAiModel() != null ? user.getAiModel() : aiClient.getDefaultConfig().getModel())
                .baseUrl(user.getAiBaseUrl() != null ? user.getAiBaseUrl() : "")
                .hasCustomApiKey(hasCustomKey)
                .maskedApiKey(maskedKey)
                .customEnabled(user.isAiCustomEnabled())
                .configured(resolved.isConfigured())
                .effectiveProvider(resolved.getProvider())
                .effectiveModel(resolved.resolveEffectiveModel())
                .systemDefaultAvailable(aiClient.getDefaultConfig().isConfigured())
                .build();
    }

    @Transactional
    public UserAiConfigDto updateUserConfig(UUID userId, UserAiConfigRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));

        if (request.getProvider() != null) {
            user.setAiProvider(request.getProvider().trim().toLowerCase());
        }
        if (request.getModel() != null) {
            user.setAiModel(request.getModel().trim());
        }
        if (request.getBaseUrl() != null) {
            user.setAiBaseUrl(request.getBaseUrl().trim());
        }
        if (request.getCustomEnabled() != null) {
            user.setAiCustomEnabled(request.getCustomEnabled());
        }
        if (Boolean.TRUE.equals(request.getClearApiKey())) {
            user.setAiApiKey(null);
        } else if (request.getApiKey() != null && !request.getApiKey().isBlank()) {
            user.setAiApiKey(request.getApiKey().trim());
        }

        user = userRepository.save(user);
        return getUserConfig(user.getId());
    }

    @Transactional
    public UserAiConfigDto resetUserConfig(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));

        user.setAiProvider(null);
        user.setAiApiKey(null);
        user.setAiModel(null);
        user.setAiBaseUrl(null);
        user.setAiCustomEnabled(false);

        user = userRepository.save(user);
        return getUserConfig(user.getId());
    }

    public AiTestResponse testConfig(UUID userId, AiTestRequest request) {
        User user = (userId != null) ? userRepository.findById(userId).orElse(null) : null;

        String apiKey = request.getApiKey();
        if ((apiKey == null || apiKey.isBlank()) && request.isUseSavedKey() && user != null) {
            apiKey = user.getAiApiKey();
        }

        String provider = request.getProvider();
        if (provider == null || provider.isBlank()) {
            provider = (user != null && user.getAiProvider() != null) ? user.getAiProvider() : aiClient.getDefaultConfig().getProvider();
        }

        String model = request.getModel();
        if (model == null || model.isBlank()) {
            model = (user != null && user.getAiModel() != null) ? user.getAiModel() : aiClient.getDefaultConfig().getModel();
        }

        String baseUrl = request.getBaseUrl();
        if (baseUrl == null && user != null) {
            baseUrl = user.getAiBaseUrl();
        }

        AiConfig configToTest = AiConfig.builder()
                .provider(provider)
                .apiKey(apiKey)
                .model(model)
                .baseUrl(baseUrl)
                .enabled(true)
                .build();

        return aiClient.testConnection(configToTest);
    }

    private String maskKey(String key) {
        if (key == null || key.isBlank()) return "";
        int len = key.length();
        if (len <= 8) {
            return "••••••••";
        }
        String prefix = key.substring(0, Math.min(6, len));
        String suffix = key.substring(Math.max(0, len - 4));
        return prefix + "••••••••" + suffix;
    }
}
