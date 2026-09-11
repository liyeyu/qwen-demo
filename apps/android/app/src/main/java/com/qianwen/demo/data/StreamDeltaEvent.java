package com.qianwen.demo.data;

public class StreamDeltaEvent extends ChatStreamEvent {
    public String messageId;
    public String conversationId;
    public String delta;
}
