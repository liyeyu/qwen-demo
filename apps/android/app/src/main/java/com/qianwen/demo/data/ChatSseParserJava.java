package com.qianwen.demo.data;

public class ChatSseParserJava {
    private String eventName = null;
    private final java.util.List<String> dataLines = new java.util.ArrayList<>();
    private final com.google.gson.Gson gson = new com.google.gson.Gson();

    public ChatStreamEvent parseLine(String line) {
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

    public ChatStreamEvent flush() {
        if (dataLines.isEmpty()) {
            eventName = null;
            return null;
        }

        String payload = String.join("\n", dataLines);
        String headerType = eventName;
        eventName = null;
        dataLines.clear();

        if ("[DONE]".equals(payload)) return null;

        // SSE 帧可能来自弱网重试或服务端异常中断，这里把解析异常收敛成 error 事件，避免 UI 流程直接崩溃。
        try {
            ChatStreamEvent event = gson.fromJson(payload, ChatStreamEvent.class);
            if (event == null) {
                return null;
            }
            if (event.type == null) {
                event.type = headerType;
            }
            if (!ChatStreamEvent.isKnownType(event.type)) {
                return null;
            }
            return event;
        } catch (Exception e) {
            return ChatStreamEvent.errorEvent("SSE 解析失败：" + (e.getMessage() == null ? "未知错误" : e.getMessage()));
        }
    }
}
