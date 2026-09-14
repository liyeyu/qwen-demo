package com.qianwen.demo.data;

import com.google.gson.Gson;
import java.io.IOException;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSource;

public class QianwenApiClientJava {
    private final String baseUrl;
    private final OkHttpClient client;
    private final Gson gson;
    private final MediaType contentType = MediaType.get("application/json; charset=utf-8");
    private volatile okhttp3.Call activeStreamCall = null;

    public QianwenApiClientJava(String baseUrl) {
        this(baseUrl, new OkHttpClient(), new Gson());
    }

    public QianwenApiClientJava(String baseUrl, OkHttpClient client, Gson gson) {
        this.baseUrl = baseUrl;
        this.client = client;
        this.gson = gson;
    }

    public <T> T get(String path, Class<T> clazz) throws IOException {
        return request(path, "GET", null, clazz);
    }

    public <T> T post(String path, Object body, Class<T> clazz) throws IOException {
        String json = gson.toJson(body);
        RequestBody rb = RequestBody.create(json, contentType);
        return request(path, "POST", rb, clazz);
    }

    public <T> T delete(String path, Class<T> clazz) throws IOException {
        return request(path, "DELETE", null, clazz);
    }

    public <T> T patch(String path, Object body, Class<T> clazz) throws IOException {
        String json = gson.toJson(body);
        RequestBody rb = RequestBody.create(json, contentType);
        return request(path, "PATCH", rb, clazz);
    }

    private <T> T request(String path, String method, RequestBody body, Class<T> clazz) throws IOException {
        Request request = new Request.Builder()
                .url(baseUrl + path)
                .method(method, body)
                .header("content-type", "application/json")
                .build();
        try (Response response = client.newCall(request).execute()) {
            String responseText = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + ": " + responseText);
            }
            return gson.fromJson(responseText, clazz);
        }
    }

    public interface ChatEventCallback {
        void onEvent(ChatStreamEvent event);
        void onError(Exception e);
    }

    public void streamChat(String conversationId, String message, ChatEventCallback callback) {
        RequestBody rb = RequestBody.create(gson.toJson(new ApiModels.ChatRequest(conversationId, message)), contentType);
        Request request = new Request.Builder()
                .url(baseUrl + "/chat/stream")
                .header("accept", "text/event-stream")
                .post(rb)
                .build();
        okhttp3.Call call = client.newCall(request);
        activeStreamCall = call;
        try (Response response = call.execute()) {
            if (!response.isSuccessful()) {
                String detail = response.body() != null ? response.body().string() : "";
                callback.onError(new IOException("HTTP " + response.code() + ": " + detail));
                return;
            }

            BufferedSource source = response.body().source();
            ChatSseParserJava parser = new ChatSseParserJava();
            boolean terminalReceived = false;
            while (!terminalReceived) {
                String line = source.readUtf8Line();
                if (line == null) break;
                ChatStreamEvent event = parser.parseLine(line);
                if (event != null) {
                    callback.onEvent(event);
                    terminalReceived = event.isTerminal();
                }
            }
            if (!terminalReceived) {
                ChatStreamEvent event = parser.flush();
                if (event != null) {
                    callback.onEvent(event);
                    terminalReceived = event.isTerminal();
                }
            }
            if (!terminalReceived) {
                callback.onError(new IOException("SSE connection interrupted without done/error event."));
            }
        } catch (Exception e) {
            if (e instanceof IOException && "Canceled".equals(e.getMessage())) {
                return;
            }
            callback.onError(e);
        } finally {
            activeStreamCall = null;
        }
    }

    public void cancelActiveStream() {
        okhttp3.Call call = activeStreamCall;
        if (call != null) {
            call.cancel();
        }
    }
}
