package com.qianwen.demo;

/**
 * 库级配置入口。
 *
 * <p>宿主在使用千问界面前调用 {@link #setBaseUrl(String)} 指向自己的服务端；
 * 不调用时使用模拟器默认地址（10.0.2.2 映射到开发机的 localhost）。
 * 由于是库模块，端点不能再用 buildConfigField 固化，必须由宿主动态注入。</p>
 */
public final class QianwenConfig {
    /** 模拟器默认服务端地址。 */
    public static final String DEFAULT_BASE_URL = "http://10.0.2.2:8787";

    private static volatile String baseUrl = DEFAULT_BASE_URL;

    private QianwenConfig() {
    }

    /** 设置服务端地址，传空值则忽略（保持当前值）。 */
    public static void setBaseUrl(String url) {
        if (url != null && !url.trim().isEmpty()) {
            baseUrl = url.trim();
        }
    }

    public static String getBaseUrl() {
        return baseUrl;
    }
}
