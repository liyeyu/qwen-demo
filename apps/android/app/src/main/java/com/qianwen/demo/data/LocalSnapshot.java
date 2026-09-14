package com.qianwen.demo.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LocalSnapshot {
    public static final int VERSION = 1;

    public int version = VERSION;
    public String savedAt = null;
    public List<Conversation> conversations = new ArrayList<>();
    public Map<String, List<ChatMessage>> messagesByConversation = new HashMap<>();
    public String selectedConversationId = null;

    public enum Status {
        EMPTY,
        RESTORED,
        CORRUPTED
    }

    /** 读取结果：快照内容 + 读取状态。 */
    public static class ReadResult {
        public final LocalSnapshot snapshot;
        public final Status status;

        public ReadResult(LocalSnapshot snapshot, Status status) {
            this.snapshot = snapshot;
            this.status = status;
        }
    }
}
