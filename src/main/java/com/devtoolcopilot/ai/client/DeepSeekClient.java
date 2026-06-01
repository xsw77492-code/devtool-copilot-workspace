package com.devtoolcopilot.ai.client;

import com.devtoolcopilot.ai.client.dto.ChatCompletionRequest;
import com.devtoolcopilot.ai.client.dto.ChatCompletionResponse;
import com.devtoolcopilot.ai.config.DeepSeekProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Component
public class DeepSeekClient {
    private final RestTemplate restTemplate;
    private final DeepSeekProperties properties;
    private final ObjectMapper objectMapper;

    public DeepSeekClient(RestTemplate restTemplate, DeepSeekProperties properties, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public String chat(String systemPrompt, String userPrompt) {
        List<ChatCompletionRequest.Message> messages = List.of(new ChatCompletionRequest.Message("user", userPrompt));
        return chat(systemPrompt, messages);
    }

    public String chat(String systemPrompt, List<ChatCompletionRequest.Message> messages) {
        String url = properties.getBaseUrl();
        if (url != null && url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        url = url + "/v1/chat/completions";

        ChatCompletionRequest req = new ChatCompletionRequest();
        req.setModel(properties.getModel());
        req.setTemperature(properties.getTemperature());
        req.setStream(false);
        List<ChatCompletionRequest.Message> all = new ArrayList<>();
        all.add(new ChatCompletionRequest.Message("system", systemPrompt));
        if (messages != null) {
            for (ChatCompletionRequest.Message m : messages) {
                if (m == null || m.getRole() == null || m.getContent() == null) continue;
                all.add(m);
            }
        }
        req.setMessages(all);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getApiKey());

        HttpEntity<ChatCompletionRequest> entity = new HttpEntity<>(req, headers);
        ResponseEntity<ChatCompletionResponse> resp;
        try {
            resp = restTemplate.exchange(url, HttpMethod.POST, entity, ChatCompletionResponse.class);
        } catch (RestClientResponseException e) {
            int status = e.getRawStatusCode();
            if (status == 401 || status == 403) {
                throw new IllegalStateException("DEEPSEEK_UNAUTHORIZED");
            }
            if (status == 429) {
                throw new IllegalStateException("DEEPSEEK_RATE_LIMIT");
            }
            throw new IllegalStateException("DEEPSEEK_HTTP_ERROR");
        } catch (RestClientException e) {
            throw new IllegalStateException("DEEPSEEK_REQUEST_FAILED");
        }

        ChatCompletionResponse body = resp.getBody();
        if (body == null || body.getChoices() == null || body.getChoices().isEmpty()) {
            throw new IllegalStateException("DEEPSEEK_EMPTY_RESPONSE");
        }
        ChatCompletionResponse.Choice c0 = body.getChoices().get(0);
        if (c0 == null || c0.getMessage() == null || c0.getMessage().getContent() == null) {
            throw new IllegalStateException("DEEPSEEK_EMPTY_MESSAGE");
        }
        return c0.getMessage().getContent();
    }

    public String chatStream(String systemPrompt,
                             List<ChatCompletionRequest.Message> messages,
                             Consumer<String> onDelta) {
        String url = properties.getBaseUrl();
        if (url != null && url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        url = url + "/v1/chat/completions";

        ChatCompletionRequest req = new ChatCompletionRequest();
        req.setModel(properties.getModel());
        req.setTemperature(properties.getTemperature());
        req.setStream(true);
        List<ChatCompletionRequest.Message> all = new ArrayList<>();
        all.add(new ChatCompletionRequest.Message("system", systemPrompt));
        if (messages != null) {
            for (ChatCompletionRequest.Message m : messages) {
                if (m == null || m.getRole() == null || m.getContent() == null) continue;
                all.add(m);
            }
        }
        req.setMessages(all);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getApiKey());
        headers.setAccept(List.of(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON));

        StringBuilder full = new StringBuilder();
        try {
            restTemplate.execute(url, HttpMethod.POST, (request) -> {
                request.getHeaders().putAll(headers);
                objectMapper.writeValue(request.getBody(), req);
            }, (resp) -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(resp.getBody(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String l = line.trim();
                        if (l.isEmpty()) continue;
                        if (!l.startsWith("data:")) continue;
                        String data = l.substring(5).trim();
                        if (data.isEmpty()) continue;
                        if ("[DONE]".equals(data)) break;

                        String delta = parseDelta(data);
                        if (delta != null && !delta.isEmpty()) {
                            full.append(delta);
                            if (onDelta != null) onDelta.accept(delta);
                        }
                    }
                }
                return null;
            });
        } catch (RestClientResponseException e) {
            int status = e.getRawStatusCode();
            if (status == 401 || status == 403) {
                throw new IllegalStateException("DEEPSEEK_UNAUTHORIZED");
            }
            if (status == 429) {
                throw new IllegalStateException("DEEPSEEK_RATE_LIMIT");
            }
            throw new IllegalStateException("DEEPSEEK_HTTP_ERROR");
        } catch (RestClientException e) {
            throw new IllegalStateException("DEEPSEEK_REQUEST_FAILED");
        } catch (Exception e) {
            throw new IllegalStateException("DEEPSEEK_REQUEST_FAILED");
        }

        return full.toString();
    }

    private String parseDelta(String dataJson) {
        try {
            JsonNode root = objectMapper.readTree(dataJson);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) return null;
            JsonNode c0 = choices.get(0);
            String content = c0.path("delta").path("content").asText(null);
            if (content != null) return content;
            return c0.path("message").path("content").asText(null);
        } catch (Exception e) {
            return null;
        }
    }
}
