package com.devtoolcopilot.ai.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
public class ChatCompletionRequest {
    private String model;
    private List<Message> messages;
    private Double temperature;
    private Boolean stream;

    @Data
    @AllArgsConstructor
    public static class Message {
        private String role;
        private String content;
    }
}
