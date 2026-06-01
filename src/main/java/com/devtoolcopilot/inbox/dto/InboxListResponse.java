package com.devtoolcopilot.inbox.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class InboxListResponse {
    private Long unreadCount;
    private Long unhandledCount;
    private List<InboxItem> list;
}

