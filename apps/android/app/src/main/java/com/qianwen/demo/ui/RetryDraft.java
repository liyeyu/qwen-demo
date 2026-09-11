package com.qianwen.demo.ui;

public class RetryDraft {
    public String conversationId;
    public String text;
    public String reason;

    public RetryDraft(String conversationId, String text, String reason) {
        this.conversationId = conversationId;
        this.text = text;
        this.reason = reason;
    }
}
