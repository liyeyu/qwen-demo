package com.qianwen.demo.data;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端接口的传输结构（请求/响应包）。
 * 这些都是单用途的瘦 DTO，集中在一个文件里，避免大量只有一两个字段的实体类散落在 data 目录。
 */
public final class ApiModels {
    private ApiModels() {
    }

    public static class Health {
        public String status;
        public String service;
        public String modelMode;
        public String timestamp;
        public String version;
    }

    public static class Conversations {
        public List<Conversation> conversations = new ArrayList<>();
    }

    public static class ConversationEnvelope {
        public Conversation conversation;
    }

    public static class Messages {
        public Conversation conversation;
        public List<ChatMessage> messages = new ArrayList<>();
    }

    public static class ChatResult {
        public Conversation conversation;
        public ChatMessage userMessage;
        public ChatMessage assistantMessage;
    }

    /** 新建会话传 title，更新会话传 title 或 pinned，两者共用同一结构。 */
    public static class ConversationRequest {
        public String title;
        public Boolean pinned;

        public ConversationRequest(String title) {
            this(title, null);
        }

        public ConversationRequest(String title, Boolean pinned) {
            this.title = title;
            this.pinned = pinned;
        }
    }

    public static class ChatRequest {
        public String conversationId;
        public String message;

        public ChatRequest(String conversationId, String message) {
            this.conversationId = conversationId;
            this.message = message;
        }
    }
}
