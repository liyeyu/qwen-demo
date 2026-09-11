package com.qianwen.demo.data;

public class ChatRequest {
    public String conversationId;
    public String message;

    public ChatRequest(String conversationId, String message) {
        this.conversationId = conversationId;
        this.message = message;
    }
}
