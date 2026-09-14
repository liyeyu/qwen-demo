package com.qianwen.demo.ui;

import com.qianwen.demo.BuildConfig;
import com.qianwen.demo.data.ApiModels;
import com.qianwen.demo.data.ChatMessage;
import com.qianwen.demo.data.Conversation;
import com.qianwen.demo.data.NativeScreen;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 页面统一状态。
 * 原来分散在 5 个文件里的状态枚举与重试草稿一并收在这里，作为 UI 状态的组成部分。
 */
public class QianwenUiState {
    public enum ServiceStatus {
        CHECKING("检查中"),
        ONLINE("在线"),
        OFFLINE("离线");

        public final String label;

        ServiceStatus(String label) {
            this.label = label;
        }
    }

    public enum ConversationListStatus {
        LOADING,
        READY,
        EMPTY,
        OFFLINE
    }

    public enum SendStatus {
        IDLE,
        STREAMING,
        FAILED,
        CANCELED
    }

    public enum CacheStatus {
        EMPTY("无缓存"),
        RESTORED("已恢复"),
        SAVED("已保存"),
        CORRUPTED("缓存异常");

        public final String label;

        CacheStatus(String label) {
            this.label = label;
        }
    }

    /** 发送失败/取消后保留的输入，用于一键重试。 */
    public static class RetryDraft {
        public final String conversationId;
        public final String text;
        public final String reason;

        public RetryDraft(String conversationId, String text, String reason) {
            this.conversationId = conversationId;
            this.text = text;
            this.reason = reason;
        }
    }

    public NativeScreen screen = NativeScreen.CONVERSATIONS;
    public List<Conversation> conversations = new ArrayList<>();
    public Map<String, List<ChatMessage>> messagesByConversation = new HashMap<>();
    public String selectedConversationId = null;
    public ApiModels.Health health = null;
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
    public String apiBaseUrl = BuildConfig.QWEN_API_BASE_URL;

    public QianwenUiState() {
    }

    public QianwenUiState(QianwenUiState other) {
        this.screen = other.screen;
        this.conversations = new ArrayList<>(other.conversations);
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

    public boolean isStreaming() {
        return sendStatus == SendStatus.STREAMING;
    }
}
