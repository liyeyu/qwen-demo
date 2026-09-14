package com.qianwen.demo.data;

public class StreamDoneEvent implements ChatStreamEvent {
    public String type = "done";
    public ChatMessage message;

    @Override
    public String getType() { return type; }
}
