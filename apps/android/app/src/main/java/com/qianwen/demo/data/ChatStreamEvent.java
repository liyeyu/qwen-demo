package com.qianwen.demo.data;

import java.util.List;

/**
 * SSE 单帧事件。
 * 原实现按 conversation/message/delta/done/error 拆成 5 个子类，实际字段高度重叠，
 * 这里合并成一个实体，用 type 做判别字段，按需读取对应字段。
 */
public class ChatStreamEvent {
    public static final String TYPE_CONVERSATION = "conversation";
    public static final String TYPE_MESSAGE = "message";
    public static final String TYPE_DELTA = "delta";
    public static final String TYPE_NEWS = "news";
    public static final String TYPE_DONE = "done";
    public static final String TYPE_ERROR = "error";

    public String type;
    public Conversation conversation;
    public ChatMessage message;
    public String messageId;
    public String conversationId;
    public String delta;
    public String error;
    /** 新闻列表消息：携带要展示的 news 数组。 */
    public List<NewsItem> news;

    public static ChatStreamEvent errorEvent(String error) {
        ChatStreamEvent event = new ChatStreamEvent();
        event.type = TYPE_ERROR;
        event.error = error;
        return event;
    }

    public static boolean isKnownType(String type) {
        return TYPE_CONVERSATION.equals(type)
                || TYPE_MESSAGE.equals(type)
                || TYPE_DELTA.equals(type)
                || TYPE_NEWS.equals(type)
                || TYPE_DONE.equals(type)
                || TYPE_ERROR.equals(type);
    }

    public boolean isDelta() {
        return TYPE_DELTA.equals(type);
    }

    public boolean isError() {
        return TYPE_ERROR.equals(type);
    }

    /** 收到 done/error 表示本次流式回复结束；news 属于流中间事件。 */
    public boolean isTerminal() {
        return TYPE_DONE.equals(type) || TYPE_ERROR.equals(type);
    }
}
