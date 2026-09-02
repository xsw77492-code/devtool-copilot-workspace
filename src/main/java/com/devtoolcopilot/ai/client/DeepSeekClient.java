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
    private static final int MAX_ATTEMPTS = 3;
    private static final long BASE_BACKOFF_MS = 1000L;

    private final RestTemplate restTemplate;
    private final DeepSeekProperties properties;
    private final ObjectMapper objectMapper;

    public DeepSeekClient(RestTemplate restTemplate, DeepSeekProperties properties, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /** 兼容旧调用：普通对话 */
    public String chat(String systemPrompt, String userPrompt) {
        List<ChatCompletionRequest.Message> messages = List.of(new ChatCompletionRequest.Message("user", userPrompt));
        return chat(systemPrompt, messages);
    }

    /** 兼容旧调用：返回纯文本 */
    public String chat(String systemPrompt, List<ChatCompletionRequest.Message> messages) {
        return chatWithUsage(systemPrompt, messages).getContent();
    }

    /** 带用量统计的对话 */
    public DeepSeekResult chatWithUsage(String systemPrompt, List<ChatCompletionRequest.Message> messages) {
        ChatCompletionRequest req = buildRequest(systemPrompt, messages, false);
        HttpHeaders headers = buildHeaders(false);
        HttpEntity<ChatCompletionRequest> entity = new HttpEntity<>(req, headers);

        Exception lastErr = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            if (attempt > 0) sleepBackoff(attempt);
            try {
                ResponseEntity<ChatCompletionResponse> resp =
                        restTemplate.exchange(baseUrl(), HttpMethod.POST, entity, ChatCompletionResponse.class);
                ChatCompletionResponse body = resp.getBody();
                if (body == null || body.getChoices() == null || body.getChoices().isEmpty()) {
                    throw new IllegalStateException("DEEPSEEK_EMPTY_RESPONSE");
                }
                ChatCompletionResponse.Choice c0 = body.getChoices().get(0);
                if (c0 == null || c0.getMessage() == null || c0.getMessage().getContent() == null) {
                    throw new IllegalStateException("DEEPSEEK_EMPTY_MESSAGE");
                }
                ChatCompletionResponse.Usage u = body.getUsage();
                return new DeepSeekResult(
                        c0.getMessage().getContent(),
                        u == null ? null : u.getPromptTokens(),
                        u == null ? null : u.getCompletionTokens(),
                        u == null ? null : u.getTotalTokens());
            } catch (RestClientResponseException e) {
                int status = e.getRawStatusCode();
                if (status == 401 || status == 403) {
                    throw new IllegalStateException("DEEPSEEK_UNAUTHORIZED");
                }
                if (status == 429) {
                    if (attempt < MAX_ATTEMPTS - 1) {
                        lastErr = e;
                        continue;
                    }
                    throw new IllegalStateException("DEEPSEEK_RATE_LIMIT");
                }
                if (status >= 500) {
                    if (attempt < MAX_ATTEMPTS - 1) {
                        lastErr = e;
                        continue;
                    }
                }
                throw new IllegalStateException("DEEPSEEK_HTTP_ERROR");
            } catch (RestClientException e) {
                if (attempt < MAX_ATTEMPTS - 1) {
                    lastErr = e;
                    continue;
                }
                throw new IllegalStateException("DEEPSEEK_REQUEST_FAILED");
            } catch (IllegalStateException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException("DEEPSEEK_REQUEST_FAILED");
            }
        }
        throw new IllegalStateException("DEEPSEEK_REQUEST_FAILED");
    }

    /** 兼容旧调用：流式返回纯文本 */
    public String chatStream(String systemPrompt,
                             List<ChatCompletionRequest.Message> messages,
                             Consumer<String> onDelta) {
        return chatStreamWithUsage(systemPrompt, messages, onDelta).getContent();
    }

    /** 带用量统计的流式对话 */
    public DeepSeekResult chatStreamWithUsage(String systemPrompt,
                                              List<ChatCompletionRequest.Message> messages,
                                              Consumer<String> onDelta) {
        ChatCompletionRequest req = buildRequest(systemPrompt, messages, true);
        HttpHeaders headers = buildHeaders(true);

        Exception lastErr = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            if (attempt > 0) sleepBackoff(attempt);
            StringBuilder full = new StringBuilder();
            final long[] usage = new long[3]; // prompt, completion, total
            try {
                restTemplate.execute(baseUrl(), HttpMethod.POST, (request) -> {
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
                            collectUsage(data, usage);
                        }
                    }
                    return null;
                });
                return new DeepSeekResult(
                        full.toString(),
                        usage[0] == 0 ? null : usage[0],
                        usage[1] == 0 ? null : usage[1],
                        usage[2] == 0 ? null : usage[2]);
            } catch (RestClientResponseException e) {
                int status = e.getRawStatusCode();
                if (status == 401 || status == 403) {
                    throw new IllegalStateException("DEEPSEEK_UNAUTHORIZED");
                }
                if (status == 429) {
                    if (attempt < MAX_ATTEMPTS - 1) {
                        lastErr = e;
                        continue;
                    }
                    throw new IllegalStateException("DEEPSEEK_RATE_LIMIT");
                }
                if (status >= 500 && attempt < MAX_ATTEMPTS - 1) {
                    lastErr = e;
                    continue;
                }
                throw new IllegalStateException("DEEPSEEK_HTTP_ERROR");
            } catch (RestClientException e) {
                if (attempt < MAX_ATTEMPTS - 1) {
                    lastErr = e;
                    continue;
                }
                throw new IllegalStateException("DEEPSEEK_REQUEST_FAILED");
            } catch (IllegalStateException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException("DEEPSEEK_REQUEST_FAILED");
            }
        }
        throw new IllegalStateException("DEEPSEEK_REQUEST_FAILED");
    }

    private ChatCompletionRequest buildRequest(String systemPrompt, List<ChatCompletionRequest.Message> messages, boolean stream) {
        String url = properties.getBaseUrl();
        ChatCompletionRequest req = new ChatCompletionRequest();
        req.setModel(properties.getModel());
        req.setTemperature(properties.getTemperature());
        req.setStream(stream);
        if (stream) {
            ChatCompletionRequest.StreamOptions so = new ChatCompletionRequest.StreamOptions();
            so.setIncludeUsage(Boolean.TRUE);
            req.setStreamOptions(so);
        }
        if (properties.getMaxTokens() != null) {
            req.setMaxTokens(properties.getMaxTokens());
        }
        List<ChatCompletionRequest.Message> all = new ArrayList<>();
        all.add(new ChatCompletionRequest.Message("system", systemPrompt));
        if (messages != null) {
            for (ChatCompletionRequest.Message m : messages) {
                if (m == null || m.getRole() == null || m.getContent() == null) continue;
                all.add(m);
            }
        }
        req.setMessages(all);
        return req;
    }

    private HttpHeaders buildHeaders(boolean stream) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getApiKey());
        if (stream) {
            headers.setAccept(List.of(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON));
        }
        return headers;
    }

    private String baseUrl() {
        String url = properties.getBaseUrl();
        if (url != null && url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url + "/v1/chat/completions";
    }

    private void sleepBackoff(int attempt) {
        try {
            Thread.sleep(BASE_BACKOFF_MS * (1L << (attempt - 1)));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private void collectUsage(String dataJson, long[] usage) {
        try {
            JsonNode root = objectMapper.readTree(dataJson);
            JsonNode u = root.path("usage");
            if (u == null || u.isMissingNode() || !u.isObject()) return;
            if (u.path("prompt_tokens").isNumber()) usage[0] = u.path("prompt_tokens").asLong();
            if (u.path("completion_tokens").isNumber()) usage[1] = u.path("completion_tokens").asLong();
            if (u.path("total_tokens").isNumber()) usage[2] = u.path("total_tokens").asLong();
        } catch (Exception ignored) {
            // 忽略 usage 解析失败
        }
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

    /** DeepSeek 调用结果（含 token 用量） */
    public static class DeepSeekResult {
        private final String content;
        private final Long promptTokens;
        private final Long completionTokens;
        private final Long totalTokens;

        public DeepSeekResult(String content, Long promptTokens, Long completionTokens, Long totalTokens) {
            this.content = content;
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
            this.totalTokens = totalTokens;
        }

        public String getContent() {
            return content;
        }

        public Long getPromptTokens() {
            return promptTokens;
        }

        public Long getCompletionTokens() {
            return completionTokens;
        }

        public Long getTotalTokens() {
            return totalTokens;
        }
    }
}
