package com.qianwen.demo.data;

public class NewsItem {
    public String title;
    public String image;    // 兼容字段名 "image"
    public String imageUrl; // 兼容字段名 "imageUrl"

    public NewsItem() {}

    public String resolvedImageUrl() {
        if (imageUrl != null && !imageUrl.isEmpty()) return imageUrl;
        if (image != null && !image.isEmpty()) return image;
        return null;
    }
}
