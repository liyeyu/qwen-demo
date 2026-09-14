package com.qianwen.demo.data;

import java.util.List;
import java.util.Map;

public class LocalSnapshot {
    public int version = 1;
    public String savedAt = null;
    public List<Conversation> conversations = java.util.Collections.emptyList();
    public Map<String, List<ChatMessage>> messagesByConversation = java.util.Collections.emptyMap();
    public String selectedConversationId = null;
}
