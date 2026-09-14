package com.qianwen.demo.data.store;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.qianwen.demo.data.Conversation;
import com.qianwen.demo.data.CreateConversationRequest;
import com.qianwen.demo.data.CreateConversationResponse;
import com.qianwen.demo.data.UpdateConversationRequest;
import com.qianwen.demo.data.ConversationsResponse;
import com.qianwen.demo.data.MessagesResponse;
import com.qianwen.demo.data.ChatResponse;
import com.qianwen.demo.data.LocalSnapshot;
import com.qianwen.demo.data.LocalSnapshotResult;
import com.qianwen.demo.data.SnapshotReadStatus;
import com.qianwen.demo.data.ChatMessage;
import com.qianwen.demo.data.ChatRequest;
import com.qianwen.demo.data.network.QianwenApiClientJava;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class QianwenRepositoryJava {
    private static final String PREF_NAME = "qianwen_native_store";
    private static final String SNAPSHOT_KEY = "snapshot";

    private final Context context;
    private final QianwenApiClientJava api;
    private final Gson gson;
    private final SharedPreferences prefs;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public QianwenRepositoryJava(Context context) {
        this(context, new QianwenApiClientJava(com.qianwen.demo.BuildConfig.QWEN_API_BASE_URL), new Gson());
    }

    public QianwenRepositoryJava(Context context, QianwenApiClientJava api, Gson gson) {
        this.context = context.getApplicationContext();
        this.api = api;
        this.gson = gson;
        this.prefs = this.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public com.qianwen.demo.data.HealthResponse health() throws Exception {
        return api.get("/health", com.qianwen.demo.data.HealthResponse.class);
    }

    public List<Conversation> listConversations() throws Exception {
        ConversationsResponse resp = api.get("/conversations", ConversationsResponse.class);
        return resp != null ? resp.conversations : java.util.Collections.emptyList();
    }

    public Conversation createConversation(String title) throws Exception {
        CreateConversationResponse resp = api.post("/conversations", new CreateConversationRequest(title), CreateConversationResponse.class);
        return resp != null ? resp.conversation : null;
    }

    public Conversation updateConversation(String conversationId, String title, Boolean pinned) throws Exception {
        CreateConversationResponse resp = api.patch("/conversations/" + conversationId, new UpdateConversationRequest(title, pinned), CreateConversationResponse.class);
        return resp != null ? resp.conversation : null;
    }

    public List<Conversation> deleteConversation(String conversationId) throws Exception {
        ConversationsResponse resp = api.delete("/conversations/" + conversationId, ConversationsResponse.class);
        return resp != null ? resp.conversations : java.util.Collections.emptyList();
    }

    public List<ChatMessage> getMessages(String conversationId) throws Exception {
        MessagesResponse resp = api.get("/conversations/" + conversationId + "/messages", MessagesResponse.class);
        return resp != null ? resp.messages : java.util.Collections.emptyList();
    }

    public List<ChatMessage> clearMessages(String conversationId) throws Exception {
        MessagesResponse resp = api.delete("/conversations/" + conversationId + "/messages", MessagesResponse.class);
        return resp != null ? resp.messages : java.util.Collections.emptyList();
    }

    public ChatResponse chat(String conversationId, String message) throws Exception {
        return api.post("/chat", new com.qianwen.demo.data.ChatRequest(conversationId, message), ChatResponse.class);
    }

    public void streamChat(String conversationId, String message, QianwenApiClientJava.ChatEventCallback callback) {
        api.streamChat(conversationId, message, callback);
    }

    public LocalSnapshotResult readSnapshot() {
        String stored = prefs.getString(SNAPSHOT_KEY, null);
        if (stored == null) {
            return new LocalSnapshotResult(new LocalSnapshot(), SnapshotReadStatus.Empty);
        }
        try {
            LocalSnapshot snapshot = gson.fromJson(stored, LocalSnapshot.class);
            return new LocalSnapshotResult(snapshot, SnapshotReadStatus.Restored);
        } catch (Exception e) {
            return new LocalSnapshotResult(new LocalSnapshot(), SnapshotReadStatus.Corrupted);
        }
    }

    public void writeSnapshot(LocalSnapshot snapshot) {
        String json = gson.toJson(snapshot);
        prefs.edit().putString(SNAPSHOT_KEY, json).apply();
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
