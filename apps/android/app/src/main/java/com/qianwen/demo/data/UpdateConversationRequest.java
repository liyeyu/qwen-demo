package com.qianwen.demo.data;

public class UpdateConversationRequest {
    public String title;
    public Boolean pinned;

    public UpdateConversationRequest() {}

    public UpdateConversationRequest(String title, Boolean pinned) {
        this.title = title;
        this.pinned = pinned;
    }
}
