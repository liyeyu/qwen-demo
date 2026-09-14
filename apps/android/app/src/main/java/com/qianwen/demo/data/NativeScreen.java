package com.qianwen.demo.data;

/** 导航目标：conversationId 为 null 表示停留在会话列表。 */
public final class NativeScreen {
    public static final NativeScreen CONVERSATIONS = new NativeScreen(null, null);

    public final String conversationId;
    public final String title;

    public NativeScreen(String conversationId, String title) {
        this.conversationId = conversationId;
        this.title = title;
    }

    public boolean isChat() {
        return conversationId != null;
    }

    @Override
    public String toString() {
        return isChat() ? "Chat(" + conversationId + ")" : "Conversations";
    }
}
