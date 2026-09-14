package com.qianwen.demo.data;

public class StreamMessageEvent implements ChatStreamEvent {
    public String type = "message";
    public ChatMessage message;

    @Override
    public String getType() { return type; }
}
