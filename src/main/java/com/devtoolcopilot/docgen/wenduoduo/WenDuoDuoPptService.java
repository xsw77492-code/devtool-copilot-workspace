package com.devtoolcopilot.docgen.wenduoduo;

import com.devtoolcopilot.common.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class WenDuoDuoPptService {
    private final WenDuoDuoProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AtomicReference<String> cachedToken = new AtomicReference<>();

    public WenDuoDuoPptService(WenDuoDuoProperties properties,
                               @Qualifier("wenduoduoRestTemplate") RestTemplate restTemplate,
                               ObjectMapper objectMapper) {
        this.properties = properties;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public byte[] generatePptxByMarkdown(String markdown, String templateId) {
        if (markdown == null || markdown.isBlank()) throw new ApiException(400, "markdown不能为空");
        String tpl = (templateId == null || templateId.isBlank()) ? properties.getTemplateId() : templateId.trim();
        String token = getToken();

        Long taskId = null;
        JsonNode taskResp = createTask(token, markdown);
        taskId = findLong(taskResp, List.of("data.taskId", "data.id", "taskId", "id"));

        JsonNode genResp = generatePptx(token, tpl, markdown, taskId);
        String url = findUrl(genResp);

        if ((url == null || url.isBlank()) && taskId != null) {
            url = pollForUrl(token, taskId);
        }
        if (url == null || url.isBlank()) throw new ApiException(502, "文多多返回缺少下载链接");

        return downloadBytes(token, url);
    }

    private String getToken() {
        String apiKey = properties.getApiKey();
        if (apiKey == null || apiKey.isBlank()) throw new ApiException(500, "文多多API Key未配置");
        if (!properties.isUseApiToken()) return apiKey.trim();
        String cached = cachedToken.get();
        if (cached != null && !cached.isBlank()) return cached;
        String token = createApiToken(apiKey.trim());
        cachedToken.set(token);
        return token;
    }

    private String createApiToken(String apiKey) {
        String url = joinUrl(properties.getBaseUrl(), "/api/user/createApiToken");
        HttpHeaders headers = new HttpHeaders();
        headers.set("Api-Key", apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>("{}", headers);
        JsonNode node = postForJson(url, entity, "createApiToken");
        String token = findText(node, List.of("data.token", "token"));
        if (token == null || token.isBlank()) throw new ApiException(502, "文多多鉴权token获取失败");
        return token.trim();
    }

    private JsonNode createTask(String token, String markdown) {
        String url = joinUrl(properties.getBaseUrl(), "/api/ppt/v2/createTask");
        HttpHeaders headers = new HttpHeaders();
        headers.set("token", token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        int type = properties.getCreateTaskType() > 0 ? properties.getCreateTaskType() : 7;
        form.add("type", String.valueOf(type));
        form.add("content", markdown);
        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(form, headers);
        return postForJson(url, entity, "createTask");
    }

    private JsonNode generatePptx(String token, String templateId, String markdown, Long taskId) {
        String url = joinUrl(properties.getBaseUrl(), "/api/ppt/v2/generatePptx");
        HttpHeaders headers = new HttpHeaders();
        headers.set("token", token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = new LinkedHashMap<>();
        if (templateId != null && !templateId.isBlank()) body.put("templateId", templateId);
        body.put("markdown", markdown);
        if (taskId != null) body.put("taskId", taskId);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        return postForJson(url, entity, "generatePptx");
    }

    private String pollForUrl(String token, Long taskId) {
        String path = properties.getQueryTaskPath();
        if (path == null || path.isBlank()) return null;
        int interval = properties.getPollIntervalMs() > 0 ? properties.getPollIntervalMs() : 1500;
        int timeout = properties.getPollTimeoutMs() > 0 ? properties.getPollTimeoutMs() : (int) Duration.ofMinutes(5).toMillis();
        long endAt = System.currentTimeMillis() + timeout;
        while (System.currentTimeMillis() < endAt) {
            JsonNode node = queryTask(token, taskId, path.trim());
            String url = findUrl(node);
            if (url != null && !url.isBlank()) return url;
            sleep(interval);
        }
        throw new ApiException(504, "文多多生成超时");
    }

    private JsonNode queryTask(String token, Long taskId, String path) {
        String url = joinUrl(properties.getBaseUrl(), path.startsWith("/") ? path : ("/" + path));
        HttpHeaders headers = new HttpHeaders();
        headers.set("token", token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("taskId", taskId);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        return postForJson(url, entity, "queryTask");
    }

    private byte[] downloadBytes(String token, String url) {
        String finalUrl = url.trim();
        if (finalUrl.startsWith("/")) finalUrl = joinUrl(properties.getBaseUrl(), finalUrl);
        HttpHeaders headers = new HttpHeaders();
        headers.set("token", token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        try {
            ResponseEntity<byte[]> resp = restTemplate.exchange(URI.create(finalUrl), HttpMethod.GET, entity, byte[].class);
            byte[] bytes = resp.getBody();
            if (bytes == null || bytes.length == 0) throw new ApiException(502, "文多多下载PPTX为空");
            return bytes;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            String msg = safeRemoteMsg(e.getResponseBodyAsString());
            if (status == 401 || status == 403) {
                throw new ApiException(502, "文多多鉴权失败");
            }
            if (status == 429) {
                throw new ApiException(503, "文多多限流：" + msg);
            }
            throw new ApiException(502, "文多多下载失败：" + msg);
        } catch (Exception e) {
            throw new ApiException(502, "文多多下载失败");
        }
    }

    private JsonNode postForJson(String url, HttpEntity<?> entity, String action) {
        try {
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            String body = resp.getBody();
            JsonNode node = body == null || body.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(body);
            assertSuccess(node, action);
            return node;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            String msg = safeRemoteMsg(e.getResponseBodyAsString());
            if (status == 401 || status == 403) {
                throw new ApiException(502, "文多多鉴权失败(" + action + ")");
            }
            if (status == 429) {
                throw new ApiException(503, "文多多限流(" + action + ")：" + msg);
            }
            throw new ApiException(502, "文多多请求失败(" + action + ")：" + msg);
        } catch (ApiException ae) {
            throw ae;
        } catch (Exception e) {
            throw new ApiException(502, "文多多请求失败(" + action + ")");
        }
    }

    private void assertSuccess(JsonNode node, String action) {
        if (node == null) return;
        JsonNode codeNode = node.get("code");
        if (codeNode != null && codeNode.isNumber()) {
            int code = codeNode.asInt();
            if (code != 0 && code != 200) {
                String msg = findText(node, List.of("msg", "message", "error"));
                if (msg == null || msg.isBlank()) msg = "code=" + code;
                if (looksLikeInsufficient(msg)) throw new ApiException(402, "文多多积分不足：" + msg);
                throw new ApiException(502, "文多多失败(" + action + ")：" + msg);
            }
        }
        JsonNode ok = node.get("success");
        if (ok != null && ok.isBoolean() && !ok.asBoolean()) {
            String msg = findText(node, List.of("msg", "message", "error"));
            if (msg == null || msg.isBlank()) msg = "success=false";
            if (looksLikeInsufficient(msg)) throw new ApiException(402, "文多多积分不足：" + msg);
            throw new ApiException(502, "文多多失败(" + action + ")：" + msg);
        }
    }

    private static String safeRemoteMsg(String raw) {
        if (raw == null) return "";
        String s = raw.replaceAll("[\\r\\n\\t]", " ").trim();
        if (s.length() > 240) s = s.substring(0, 240).trim();
        return s;
    }

    private static void sleep(int ms) {
        try {
            Thread.sleep(Math.max(50, ms));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private static String joinUrl(String baseUrl, String path) {
        String base = baseUrl == null || baseUrl.isBlank() ? "https://wenduoduo.cn" : baseUrl.trim();
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        String p = path == null ? "" : path.trim();
        if (!p.startsWith("/")) p = "/" + p;
        return base + p;
    }

    private static String findText(JsonNode node, List<String> paths) {
        if (node == null || paths == null) return null;
        for (String p : paths) {
            JsonNode v = getByPath(node, p);
            if (v != null && v.isTextual()) {
                String s = v.asText();
                if (s != null && !s.isBlank()) return s;
            }
        }
        return null;
    }

    private static Long findLong(JsonNode node, List<String> paths) {
        if (node == null || paths == null) return null;
        for (String p : paths) {
            JsonNode v = getByPath(node, p);
            if (v != null && v.isNumber()) return v.asLong();
            if (v != null && v.isTextual()) {
                String s = v.asText();
                if (s != null && !s.isBlank()) {
                    try {
                        return Long.parseLong(s.trim());
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return null;
    }

    private static JsonNode getByPath(JsonNode node, String path) {
        if (node == null || path == null || path.isBlank()) return null;
        String[] parts = path.split("\\.");
        JsonNode cur = node;
        for (String part : parts) {
            if (cur == null) return null;
            cur = cur.get(part);
        }
        return cur;
    }

    private static String findUrl(JsonNode node) {
        if (node == null) return null;
        Deque<JsonNode> dq = new ArrayDeque<>();
        dq.add(node);
        while (!dq.isEmpty()) {
            JsonNode cur = dq.pollFirst();
            if (cur == null) continue;
            if (cur.isTextual()) {
                String s = cur.asText();
                if (looksLikeUrl(s)) return s;
            } else if (cur.isObject()) {
                cur.fields().forEachRemaining(e -> dq.addLast(e.getValue()));
            } else if (cur.isArray()) {
                cur.forEach(dq::addLast);
            }
        }
        return null;
    }

    private static boolean looksLikeUrl(String s) {
        if (s == null) return false;
        String v = s.trim();
        if (!(v.startsWith("http://") || v.startsWith("https://") || v.startsWith("/"))) return false;
        String low = v.toLowerCase();
        return low.contains("ppt") || low.endsWith(".pptx") || low.contains("download");
    }

    private static boolean looksLikeInsufficient(String msg) {
        if (msg == null) return false;
        String s = msg.trim().toLowerCase();
        return s.contains("积分") || s.contains("余额") || s.contains("insufficient") || s.contains("not enough");
    }
}
