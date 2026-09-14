package com.qianwen.demo.data;

/** 新闻列表消息中的一条新闻（列表以 news 字段下发）。 */
public class NewsItem {
    public String id;
    public String title;
    public String img;
    /** 点击跳转的原文链接，可空。 */
    public String url;
}
