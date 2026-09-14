package com.qianwen.demo.ui;

// delta merging helper used by QianwenViewModelJava
public class DeltaAccum {
    public final String conversationId;
    public final String messageId;
    public final StringBuilder sb = new StringBuilder();

    public DeltaAccum(String conversationId, String messageId) {
        this.conversationId = conversationId;
        this.messageId = messageId;
    }
}
