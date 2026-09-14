package com.qianwen.demo.data;

public class NativeScreen {
    private final String name;
    public static final NativeScreen Conversations = new NativeScreen("Conversations");
    public static final NativeScreen Status = new NativeScreen("Status");
    public static final NativeScreen Settings = new NativeScreen("Settings");

    public NativeScreen(String name) {
        this.name = name;
    }

    public static class Chat extends NativeScreen {
        public final String conversationId;
        public final String title;

        public Chat(String conversationId, String title) {
            super("Chat");
            this.conversationId = conversationId;
            this.title = title;
        }
    }
}
