package com.qianwen.demo.data;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class QianwenRepositoryJava {
    private static final String PREF_NAME = "qianwen_native_store";
    private static final String SNAPSHOT_KEY = "snapshot";

    private final QianwenApiClientJava api;
    private final Gson gson;
    private final SharedPreferences prefs;

    public QianwenRepositoryJava(Context context) {
        this(context, new QianwenApiClientJava(com.qianwen.demo.BuildConfig.QWEN_API_BASE_URL), new Gson());
    }

    public QianwenRepositoryJava(Context context, QianwenApiClientJava api, Gson gson) {
        this.api = api;
        this.gson = gson;
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public ApiModels.Health health() throws Exception {
        return api.get("/health", ApiModels.Health.class);
    }

    public List<Conversation> listConversations() throws Exception {
        ApiModels.Conversations response = api.get("/conversations", ApiModels.Conversations.class);
        return response != null && response.conversations != null
                ? response.conversations
                : Collections.<Conversation>emptyList();
    }

    public Conversation createConversation(String title) throws Exception {
        ApiModels.ConversationEnvelope response = api.post(
                "/conversations", new ApiModels.ConversationRequest(title), ApiModels.ConversationEnvelope.class);
        return response != null ? response.conversation : null;
    }

    public Conversation updateConversation(String conversationId, String title, Boolean pinned) throws Exception {
        ApiModels.ConversationRequest body = new ApiModels.ConversationRequest(title, pinned);
        ApiModels.ConversationEnvelope response = api.patch(
                "/conversations/" + conversationId, body, ApiModels.ConversationEnvelope.class);
        return response != null ? response.conversation : null;
    }

    public List<Conversation> deleteConversation(String conversationId) throws Exception {
        ApiModels.Conversations response = api.delete("/conversations/" + conversationId, ApiModels.Conversations.class);
        return response != null && response.conversations != null
                ? response.conversations
                : Collections.<Conversation>emptyList();
    }

    public List<ChatMessage> getMessages(String conversationId) throws Exception {
        ApiModels.Messages response = api.get(
                "/conversations/" + conversationId + "/messages", ApiModels.Messages.class);
        return response != null && response.messages != null
                ? response.messages
                : Collections.<ChatMessage>emptyList();
    }

    public List<ChatMessage> clearMessages(String conversationId) throws Exception {
        ApiModels.Messages response = api.delete(
                "/conversations/" + conversationId + "/messages", ApiModels.Messages.class);
        return response != null && response.messages != null
                ? response.messages
                : Collections.<ChatMessage>emptyList();
    }

    public ApiModels.ChatResult chat(String conversationId, String message) throws Exception {
        return api.post("/chat", new ApiModels.ChatRequest(conversationId, message), ApiModels.ChatResult.class);
    }

    public void streamChat(String conversationId, String message, QianwenApiClientJava.ChatEventCallback callback) {
        api.streamChat(conversationId, message, callback);
    }

    public void cancelStream() {
        api.cancelActiveStream();
    }

    public LocalSnapshot.ReadResult readSnapshot() {
        String stored = prefs.getString(SNAPSHOT_KEY, null);
        if (stored == null) {
            return new LocalSnapshot.ReadResult(new LocalSnapshot(), LocalSnapshot.Status.EMPTY);
        }
        try {
            LocalSnapshot snapshot = gson.fromJson(stored, LocalSnapshot.class);
            if (snapshot == null) {
                return new LocalSnapshot.ReadResult(new LocalSnapshot(), LocalSnapshot.Status.CORRUPTED);
            }
            return new LocalSnapshot.ReadResult(snapshot, LocalSnapshot.Status.RESTORED);
        } catch (Exception e) {
            return new LocalSnapshot.ReadResult(new LocalSnapshot(), LocalSnapshot.Status.CORRUPTED);
        }
    }

    public void writeSnapshot(LocalSnapshot snapshot) throws IOException {
        boolean stored = prefs.edit().putString(SNAPSHOT_KEY, gson.toJson(snapshot)).commit();
        if (!stored) {
            throw new IOException("snapshot write failed");
        }
    }

    public void shutdown() {
        cancelStream();
    }
}
