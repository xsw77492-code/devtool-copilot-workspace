package com.devtoolcopilot.integration.gitee.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

@Component
public class GiteeClient {
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public GiteeClient(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public List<PullRequest> listPullRequests(String owner, String repo, String accessToken, int perPage) {
        String url = UriComponentsBuilder.fromHttpUrl("https://gitee.com/api/v5/repos/{owner}/{repo}/pulls")
                .queryParam("access_token", accessToken)
                .queryParam("state", "all")
                .queryParam("sort", "updated")
                .queryParam("direction", "desc")
                .queryParam("page", 1)
                .queryParam("per_page", perPage)
                .buildAndExpand(owner, repo)
                .toUriString();
        JsonNode root = getJson(url);
        List<PullRequest> list = new ArrayList<>();
        if (root != null && root.isArray()) {
            for (JsonNode it : root) {
                PullRequest pr = PullRequest.from(it);
                if (pr != null) list.add(pr);
            }
        }
        return list;
    }

    public PullRequest getPullRequest(String owner, String repo, String accessToken, int number) {
        String url = UriComponentsBuilder.fromHttpUrl("https://gitee.com/api/v5/repos/{owner}/{repo}/pulls/{number}")
                .queryParam("access_token", accessToken)
                .buildAndExpand(owner, repo, number)
                .toUriString();
        JsonNode root = getJson(url);
        return PullRequest.from(root);
    }

    public List<Commit> listPullRequestCommits(String owner, String repo, String accessToken, int number, int perPage) {
        String url = UriComponentsBuilder.fromHttpUrl("https://gitee.com/api/v5/repos/{owner}/{repo}/pulls/{number}/commits")
                .queryParam("access_token", accessToken)
                .queryParam("page", 1)
                .queryParam("per_page", perPage)
                .buildAndExpand(owner, repo, number)
                .toUriString();
        JsonNode root = getJson(url);
        List<Commit> list = new ArrayList<>();
        if (root != null && root.isArray()) {
            for (JsonNode it : root) {
                Commit c = Commit.from(it);
                if (c != null) list.add(c);
            }
        }
        return list;
    }

    public CommitStatus getCommitStatus(String owner, String repo, String accessToken, String sha) {
        String url = UriComponentsBuilder.fromHttpUrl("https://gitee.com/api/v5/repos/{owner}/{repo}/commits/{sha}/status")
                .queryParam("access_token", accessToken)
                .buildAndExpand(owner, repo, sha)
                .toUriString();
        JsonNode root = getJson(url);
        return CommitStatus.from(root);
    }

    private JsonNode getJson(String url) {
        try {
            String raw = restTemplate.getForObject(url, String.class);
            if (raw == null || raw.isBlank()) {
                throw new IllegalStateException("GITEE_EMPTY_RESPONSE");
            }
            return objectMapper.readTree(raw);
        } catch (RestClientResponseException e) {
            if (e.getRawStatusCode() == 401 || e.getRawStatusCode() == 403) {
                throw new IllegalStateException("GITEE_UNAUTHORIZED");
            }
            if (e.getRawStatusCode() == 429) {
                throw new IllegalStateException("GITEE_RATE_LIMIT");
            }
            throw new IllegalStateException("GITEE_HTTP_ERROR");
        } catch (RestClientException e) {
            throw new IllegalStateException("GITEE_REQUEST_FAILED");
        } catch (Exception e) {
            throw new IllegalStateException("GITEE_PARSE_ERROR");
        }
    }

    public record PullRequest(Integer number, String title, String state, Boolean merged, String htmlUrl, String updatedAt) {
        static PullRequest from(JsonNode node) {
            if (node == null || node.isMissingNode() || node.isNull()) return null;
            Integer number = node.hasNonNull("number") ? node.get("number").asInt() : null;
            String title = node.hasNonNull("title") ? node.get("title").asText() : null;
            String state = node.hasNonNull("state") ? node.get("state").asText() : null;
            Boolean merged = node.hasNonNull("merged") ? node.get("merged").asBoolean() : null;
            String htmlUrl = node.hasNonNull("html_url") ? node.get("html_url").asText() : null;
            String updatedAt = node.hasNonNull("updated_at") ? node.get("updated_at").asText() : null;
            if (number == null || title == null) return null;
            return new PullRequest(number, title, state, merged, htmlUrl, updatedAt);
        }
    }

    public record Commit(String sha) {
        static Commit from(JsonNode node) {
            if (node == null || node.isMissingNode() || node.isNull()) return null;
            String sha = node.hasNonNull("sha") ? node.get("sha").asText() : null;
            if (sha == null || sha.isBlank()) return null;
            return new Commit(sha);
        }
    }

    public record CommitStatus(String state, String targetUrl, String description) {
        static CommitStatus from(JsonNode node) {
            if (node == null || node.isMissingNode() || node.isNull()) return null;
            String state = node.hasNonNull("state") ? node.get("state").asText() : null;
            String targetUrl = node.hasNonNull("target_url") ? node.get("target_url").asText() : null;
            String description = node.hasNonNull("description") ? node.get("description").asText() : null;
            return new CommitStatus(state, targetUrl, description);
        }
    }
}

