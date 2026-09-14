package com.qianwen.demo.data;

public class StreamConversationEvent implements ChatStreamEvent {
    public String type = "conversation";
    public Conversation conversation;

    @Override
    public String getType() { return type; }
}
