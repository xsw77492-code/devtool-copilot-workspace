package com.devtoolcopilot.ai.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
public class ChatCompletionRequest {
    private String model;
    private List<Message> messages;
    private Double temperature;
    private Boolean stream;

    @JsonProperty("max_tokens")
    private Integer maxTokens;

    @JsonProperty("stream_options")
    private StreamOptions streamOptions;

    @Data
    public static class StreamOptions {
        @JsonProperty("include_usage")
        private Boolean includeUsage;
    }

    @Data
    @AllArgsConstructor
    public static class Message {
        private String role;
        private String content;
    }
}
