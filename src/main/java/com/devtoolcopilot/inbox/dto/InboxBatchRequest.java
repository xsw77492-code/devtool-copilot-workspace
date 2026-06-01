package com.devtoolcopilot.inbox.dto;

import lombok.Data;

import java.util.List;

@Data
public class InboxBatchRequest {
    private List<Long> ids;
}

