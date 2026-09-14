package com.qianwen.demo.data;

public class StreamDeltaEvent implements ChatStreamEvent {
    public String type = "delta";
    public String messageId;
    public String conversationId;
    public String delta;

    @Override
    public String getType() { return type; }
}
