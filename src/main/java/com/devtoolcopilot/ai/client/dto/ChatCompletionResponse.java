package com.devtoolcopilot.ai.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class ChatCompletionResponse {
    private List<Choice> choices;
    private Usage usage;

    @Data
    public static class Choice {
        private Message message;
        private Message delta;
    }

    @Data
    public static class Message {
        private String role;
        private String content;
    }

    @Data
    public static class Usage {
        @JsonProperty("prompt_tokens")
        private Long promptTokens;

        @JsonProperty("completion_tokens")
        private Long completionTokens;

        @JsonProperty("total_tokens")
        private Long totalTokens;
    }
}
