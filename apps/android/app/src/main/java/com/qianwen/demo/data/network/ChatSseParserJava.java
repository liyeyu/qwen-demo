package com.qianwen.demo.data.network;

public class ChatSseParserJava {
    private String eventName = null;
    private java.util.List<String> dataLines = new java.util.ArrayList<>();
    private com.google.gson.Gson gson = new com.google.gson.Gson();

    public com.qianwen.demo.data.ChatStreamEvent parseLine(String line) {
        if (line.startsWith(":")) {
            return null;
        }

        if (line.trim().isEmpty()) {
            return flush();
        }

        if (line.startsWith("event:")) {
            eventName = line.substring("event:".length()).trim();
            return null;
        }

        if (line.startsWith("data:")) {
            String data = line.substring("data:".length());
            if (data.startsWith(" ")) data = data.substring(1);
            dataLines.add(data);
            return null;
        }

        return null;
    }

    public com.qianwen.demo.data.ChatStreamEvent flush() {
        if (dataLines.isEmpty()) {
            eventName = null;
            return null;
        }

        String payload = String.join("\n", dataLines);
        String type = null;
        try {
            com.google.gson.JsonObject json = gson.fromJson(payload, com.google.gson.JsonObject.class);
            if (json != null && json.has("type") && !json.get("type").isJsonNull()) {
                type = json.get("type").getAsString();
            }
        } catch (Exception ignored) {
        }

        if (type == null) type = eventName;
        eventName = null;
        dataLines.clear();

        if ("[DONE]".equals(payload)) return null;

        try {
            switch (type) {
                case "conversation":
                    return gson.fromJson(payload, com.qianwen.demo.data.StreamConversationEvent.class);
                case "message":
                    return gson.fromJson(payload, com.qianwen.demo.data.StreamMessageEvent.class);
                case "delta":
                    return gson.fromJson(payload, com.qianwen.demo.data.StreamDeltaEvent.class);
                case "done":
                    return gson.fromJson(payload, com.qianwen.demo.data.StreamDoneEvent.class);
                case "error":
                    return gson.fromJson(payload, com.qianwen.demo.data.StreamErrorEvent.class);
                default:
                    return null;
            }
        } catch (Exception e) {
            com.qianwen.demo.data.StreamErrorEvent err = new com.qianwen.demo.data.StreamErrorEvent();
            err.error = "SSE parse failed: " + (e.getMessage() == null ? "unknown" : e.getMessage());
            return err;
        }
    }
}
