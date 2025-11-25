package com.ris.rms.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class LlmClientService {

    private final RestClient restClient;
    private final String apiKey;
    private final String apiUrl;
    private final String model;
    private final int maxTokens;
    private final double temperature;
    private final boolean featureEnabled;
    private final boolean reasoningEnabled;

    public LlmClientService(RestClient.Builder builder,
                            @Value("${ai.api.key:}") String apiKey,
                            @Value("${ai.api.url:https://openrouter.ai/api/v1/chat/completions}") String apiUrl,
                            @Value("${ai.api.model:x-ai/grok-4.1-fast:free}") String model,
                            @Value("${ai.api.maxTokens:1000}") int maxTokens,
                            @Value("${ai.api.temperature:0.2}") double temperature,
                            @Value("${ai.api.readTimeoutMs:15000}") int readTimeoutMs,
                            @Value("${ai.feature.enabled:false}") boolean featureEnabled,
                            @Value("${ai.api.reasoningEnabled:false}") boolean reasoningEnabled) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(readTimeoutMs);

        this.restClient = builder.requestFactory(factory).build();

        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.model = model;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.featureEnabled = featureEnabled;
        this.reasoningEnabled = reasoningEnabled;
    }

    public boolean isEnabled() {
        return featureEnabled
                && apiKey != null && !apiKey.isBlank()
                && apiUrl != null && !apiUrl.isBlank()
                && model != null && !model.isBlank();
    }

    public String getMatchAnalysis(String prompt) {
        if (!isEnabled()) {
            throw new IllegalStateException("AI Feature disabled or API Key missing");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("messages", List.of(
                Map.of("role", "system",
                        "content", "You are an expert HR recruiter. Analyze the candidate against the job description. "
                                + "Output ONLY a JSON object with format: {\"score\": <0-100>, \"reasoning\": \"<short_summary>\"}."),
                Map.of("role", "user", "content", prompt)));
        payload.put("max_tokens", maxTokens);
        payload.put("temperature", temperature);

        if (reasoningEnabled) {
            payload.put("reasoning", Map.of("effort", "medium"));
        }

        return restClient.post()
                .uri(apiUrl)
                .header("Authorization", "Bearer " + apiKey)
                .header("X-Title", "RMS-App")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(String.class);
    }

    public String getModel() {
        return this.model;
    }
}
