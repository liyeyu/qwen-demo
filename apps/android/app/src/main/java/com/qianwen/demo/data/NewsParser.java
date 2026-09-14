package com.qianwen.demo.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;

public class NewsParser {
    private static final Gson GSON = new Gson();

    /**
     * 解析 content 为新闻列表，支持：
     * 1) 直接数组： [{ "title":"...", "image":"..." }, ...]
     * 2) 包裹形式： { "type":"news_list", "items":[ ... ] }
     * 返回空列表表示无法解析或无数据。
     */
    public static List<NewsItem> parse(String content) {
        if (content == null) return Collections.emptyList();
        String trimmed = content.trim();
        if (trimmed.isEmpty()) return Collections.emptyList();

        try {
            JsonElement el = JsonParser.parseString(trimmed);
            if (el.isJsonArray()) {
                Type listType = new TypeToken<List<NewsItem>>(){}.getType();
                return GSON.fromJson(el, listType);
            }
            if (el.isJsonObject()) {
                JsonElement itemsEl = el.getAsJsonObject().get("items");
                if (itemsEl != null && itemsEl.isJsonArray()) {
                    Type listType = new TypeToken<List<NewsItem>>(){}.getType();
                    return GSON.fromJson(itemsEl, listType);
                }
            }
        } catch (Exception e) {
            // 解析失败时返回空列表（保持稳健）
        }
        return Collections.emptyList();
    }
}
