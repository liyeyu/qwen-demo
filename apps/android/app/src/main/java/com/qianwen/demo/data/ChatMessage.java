package com.qianwen.demo.data;

import java.util.List;

public class ChatMessage {
    /** 普通文本消息。 */
    public static final String TYPE_TEXT = "text";
    /** 新闻列表消息：正文为空，内容在 news 字段里。 */
    public static final String TYPE_NEWS = "news";

    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";

    public String id;
    public String conversationId;
    public String role;
    public String content;
    public String status;
    public String createdAt;
    public String updatedAt;
    public String error;
    /** 消息类型，默认文本；历史缓存里没有该字段时按文本处理。 */
    public String type = TYPE_TEXT;
    /** 仅 type = news 时有值。 */
    public List<NewsItem> news = null;

    public boolean isUser() {
        return ROLE_USER.equals(role);
    }

    public boolean isNews() {
        return TYPE_NEWS.equals(type);
    }

    /** 真正可以渲染成新闻卡片的消息（类型正确且列表非空）。 */
    public boolean hasNews() {
        return isNews() && news != null && !news.isEmpty();
    }
}
