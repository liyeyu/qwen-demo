package com.qianwen.demo.ui;

import com.qianwen.demo.data.ChatMessage;
import com.qianwen.demo.data.Conversation;
import com.qianwen.demo.data.HealthResponse;
import com.qianwen.demo.data.LocalSnapshot;
import com.qianwen.demo.data.SnapshotReadStatus;
import com.qianwen.demo.data.NativeScreen;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QianwenUiState {
    public NativeScreen screen = NativeScreen.Conversations;
    public List<Conversation> conversations = java.util.Collections.emptyList();
    public Map<String, List<ChatMessage>> messagesByConversation = new HashMap<>();
    public String selectedConversationId = null;
    public HealthResponse health = null;
    public ServiceStatus serviceStatus = ServiceStatus.CHECKING;
    public ConversationListStatus listStatus = ConversationListStatus.LOADING;
    public SendStatus sendStatus = SendStatus.IDLE;
    public CacheStatus cacheStatus = CacheStatus.EMPTY;
    public RetryDraft retryDraft = null;
    public String draft = "";
    public String error = null;
    public String notice = null;
    public String searchQuery = "";
    public String lastHealthCheckedAt = null;
    public String lastCacheSavedAt = null;
    public String apiBaseUrl = null;

    public boolean isStreaming() {
        return sendStatus == SendStatus.STREAMING;
    }

    public QianwenUiState() {}

    public QianwenUiState(QianwenUiState other) {
        this.screen = other.screen;
        this.conversations = other.conversations;
        this.messagesByConversation = new HashMap<>(other.messagesByConversation);
        this.selectedConversationId = other.selectedConversationId;
        this.health = other.health;
        this.serviceStatus = other.serviceStatus;
        this.listStatus = other.listStatus;
        this.sendStatus = other.sendStatus;
        this.cacheStatus = other.cacheStatus;
        this.retryDraft = other.retryDraft;
        this.draft = other.draft;
        this.error = other.error;
        this.notice = other.notice;
        this.searchQuery = other.searchQuery;
        this.lastHealthCheckedAt = other.lastHealthCheckedAt;
        this.lastCacheSavedAt = other.lastCacheSavedAt;
        this.apiBaseUrl = other.apiBaseUrl;
    }
}
