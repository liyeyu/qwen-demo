package com.qianwen.demo.data;

public class StreamErrorEvent implements ChatStreamEvent {
    public String type = "error";
    public String messageId;
    public String conversationId;
    public String error;

    @Override
    public String getType() { return type; }
}
