package com.qianwen.demo.ui;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.qianwen.demo.BuildConfig;
import com.qianwen.demo.data.ApiModels;
import com.qianwen.demo.data.ChatMessage;
import com.qianwen.demo.data.ChatStreamEvent;
import com.qianwen.demo.data.Conversation;
import com.qianwen.demo.data.LocalSnapshot;
import com.qianwen.demo.data.NativeScreen;
import com.qianwen.demo.data.NewsItem;
import com.qianwen.demo.data.QianwenApiClientJava;
import com.qianwen.demo.data.QianwenRepositoryJava;
import com.qianwen.demo.ui.QianwenUiState.CacheStatus;
import com.qianwen.demo.ui.QianwenUiState.ConversationListStatus;
import com.qianwen.demo.ui.QianwenUiState.RetryDraft;
import com.qianwen.demo.ui.QianwenUiState.SendStatus;
import com.qianwen.demo.ui.QianwenUiState.ServiceStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class QianwenViewModelJava extends ViewModel {
    private static final int FLUSH_DELAY_MS = 120;
    private static final double EMA_ALPHA = 0.3;
    private static final int MIN_DELAY_MS = 40;
    private static final int MAX_DELAY_MS = 300;

    private final QianwenRepositoryJava repository;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final MutableLiveData<QianwenUiState> liveState = new MutableLiveData<>(new QianwenUiState());
    public final LiveData<QianwenUiState> state = liveState;

    private final Object stateLock = new Object();
    private final QianwenUiState current = new QianwenUiState();

    private volatile Future<?> sendFuture = null;
    private volatile boolean sendCanceled = false;
    // UI 已经显式发起过导航时，本地缓存恢复不再覆盖会话选择。
    private volatile boolean screenRequestedByUi = false;

    // 增量合并缓冲：将高频 delta 按 messageId 累积后统一刷新，降低 UI 更新频率。
    private final Object deltaLock = new Object();
    private final Map<String, DeltaAccum> deltaBuffer = new HashMap<>();
    private final Runnable flushRunnable = new Runnable() {
        @Override
        public void run() {
            flushDeltas();
        }
    };
    private long lastDeltaTimestamp = 0L;
    private double emaIntervalMs = FLUSH_DELAY_MS;

    private interface Mutation {
        void apply(QianwenUiState state);
    }

    /** 增量合并缓冲单元：把同一 messageId 的高频 delta 先累积，再统一落屏。 */
    private static final class DeltaAccum {
        final String conversationId;
        final String messageId;
        final StringBuilder sb = new StringBuilder();

        DeltaAccum(String conversationId, String messageId) {
            this.conversationId = conversationId;
            this.messageId = messageId;
        }
    }

    public QianwenViewModelJava(QianwenRepositoryJava repository, String configuredApiBaseUrl) {
        this.repository = repository;
        synchronized (stateLock) {
            current.apiBaseUrl = configuredApiBaseUrl;
        }
        liveState.setValue(new QianwenUiState(current));
        executor.submit(new Runnable() {
            @Override
            public void run() {
                restoreLocalSnapshot();
                refreshHealth();
                refreshConversations();
            }
        });
    }

    public QianwenViewModelJava(QianwenRepositoryJava repository) {
        this(repository, BuildConfig.QWEN_API_BASE_URL);
    }

    public static ViewModelProvider.Factory factory(final Application application) {
        return new ViewModelProvider.Factory() {
            @SuppressWarnings("unchecked")
            @Override
            public <T extends ViewModel> T create(Class<T> modelClass) {
                if (modelClass.isAssignableFrom(QianwenViewModelJava.class)) {
                    return (T) new QianwenViewModelJava(new QianwenRepositoryJava(application));
                }
                throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
            }
        };
    }

    private QianwenUiState snapshot() {
        synchronized (stateLock) {
            return new QianwenUiState(current);
        }
    }

    private void mutate(Mutation mutation) {
        QianwenUiState copy;
        synchronized (stateLock) {
            mutation.apply(current);
            copy = new QianwenUiState(current);
        }
        liveState.postValue(copy);
    }

    // region 导航与列表

    public void navigate(NativeScreen screen) {
        screenRequestedByUi = true;
        mutate(s -> {
            s.screen = screen;
            s.error = null;
        });
        if (screen.isChat()) {
            selectConversation(screen.conversationId, screen.title);
        }
    }

    public void refreshHealth() {
        refreshHealth(false);
    }

    public void refreshHealth(final boolean showFeedback) {
        mutate(s -> s.serviceStatus = ServiceStatus.CHECKING);
        executor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    final ApiModels.Health health = repository.health();
                    mutate(s -> {
                        s.health = health;
                        s.serviceStatus = ServiceStatus.ONLINE;
                        s.lastHealthCheckedAt = Instant.now().toString();
                        s.error = null;
                        if (showFeedback) {
                            s.notice = "服务端在线：" + (health == null ? "unknown" : health.modelMode);
                        }
                    });
                } catch (Exception error) {
                    mutate(s -> {
                        s.serviceStatus = ServiceStatus.OFFLINE;
                        s.lastHealthCheckedAt = Instant.now().toString();
                        s.error = "服务端健康检查失败，当前可继续查看本地缓存。";
                        if (showFeedback) {
                            s.notice = "服务端状态刷新失败。";
                        }
                    });
                }
            }
        });
    }

    public void showNotice(String message) {
        mutate(s -> s.notice = message);
    }

    public void clearNotice() {
        mutate(s -> s.notice = null);
    }

    public void refreshConversations() {
        mutate(s -> s.listStatus = ConversationListStatus.LOADING);
        executor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<Conversation> remote = repository.listConversations();
                    mutate(s -> {
                        s.conversations = mergeConversations(remote, s.conversations);
                        if (s.selectedConversationId == null) {
                            s.selectedConversationId = s.conversations.isEmpty() ? null : s.conversations.get(0).id;
                        }
                        s.screen = syncScreenTitle(s.screen, s.conversations);
                        s.listStatus = toListStatus(s.conversations);
                        s.error = null;
                    });
                    persistAsync();
                } catch (Exception error) {
                    mutate(s -> {
                        s.listStatus = s.conversations.isEmpty()
                                ? ConversationListStatus.OFFLINE
                                : ConversationListStatus.READY;
                        s.serviceStatus = ServiceStatus.OFFLINE;
                        s.error = "无法连接服务端，已保留本地会话与消息。";
                    });
                }
            }
        });
    }

    public void createConversation() {
        mutate(s -> s.listStatus = ConversationListStatus.LOADING);
        executor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    final Conversation created = repository.createConversation("Android 会话");
                    if (created == null) {
                        throw new IllegalStateException("服务端未返回会话");
                    }
                    mutate(s -> {
                        s.screen = new NativeScreen(created.id, created.title);
                        s.conversations = sortConversations(upsertConversation(s.conversations, created));
                        s.selectedConversationId = created.id;
                        s.listStatus = ConversationListStatus.READY;
                        s.draft = "";
                        s.retryDraft = null;
                        s.sendStatus = SendStatus.IDLE;
                        s.error = null;
                        s.notice = "已新建会话。";
                    });
                    persistAsync();
                } catch (Exception error) {
                    mutate(s -> {
                        s.listStatus = toListStatus(s.conversations);
                        s.error = "新建会话失败，请确认服务端已启动。";
                    });
                }
            }
        });
    }

    public void updateSearchQuery(String query) {
        mutate(s -> s.searchQuery = query == null ? "" : query);
    }

    public void renameConversation(final String conversationId, String title) {
        final String nextTitle = title == null ? "" : title.trim();
        if (nextTitle.isEmpty()) {
            return;
        }
        executor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    final Conversation updated = repository.updateConversation(conversationId, nextTitle, null);
                    if (updated == null) {
                        throw new IllegalStateException("服务端未返回会话");
                    }
                    mutate(s -> {
                        s.conversations = sortConversations(upsertConversation(s.conversations, updated));
                        s.screen = syncScreenTitle(s.screen, Collections.singletonList(updated));
                        s.error = null;
                        s.notice = "会话已重命名。";
                    });
                    persistAsync();
                } catch (Exception error) {
                    mutate(s -> s.error = "重命名失败，请稍后重试。");
                }
            }
        });
    }

    public void togglePinned(final Conversation conversation) {
        if (conversation == null) {
            return;
        }
        executor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    final Conversation updated = repository.updateConversation(conversation.id, null, !conversation.pinned);
                    if (updated == null) {
                        throw new IllegalStateException("服务端未返回会话");
                    }
                    mutate(s -> {
                        s.conversations = sortConversations(upsertConversation(s.conversations, updated));
                        s.error = null;
                        s.notice = updated.pinned ? "会话已置顶。" : "已取消置顶。";
                    });
                    persistAsync();
                } catch (Exception error) {
                    mutate(s -> s.error = "置顶状态同步失败。");
                }
            }
        });
    }

    public void deleteConversation(final String conversationId) {
        QianwenUiState snapshot = snapshot();
        if (conversationId != null
                && conversationId.equals(snapshot.selectedConversationId)
                && snapshot.isStreaming()) {
            cancelSending();
        }
        executor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<Conversation> remote = repository.deleteConversation(conversationId);
                    mutate(s -> {
                        s.messagesByConversation.remove(conversationId);
                        s.conversations = sortConversations(remote);
                        if (Objects.equals(s.selectedConversationId, conversationId)) {
                            s.selectedConversationId = s.conversations.isEmpty() ? null : s.conversations.get(0).id;
                        }
                        s.screen = NativeScreen.CONVERSATIONS;
                        s.listStatus = toListStatus(s.conversations);
                        if (s.retryDraft != null && Objects.equals(s.retryDraft.conversationId, conversationId)) {
                            s.retryDraft = null;
                        }
                        s.error = null;
                        s.notice = "会话已删除。";
                    });
                    persistAsync();
                } catch (Exception error) {
                    mutate(s -> s.error = "删除会话失败，请稍后重试。");
                }
            }
        });
    }

    public void selectConversation(final String conversationId, final String title) {
        screenRequestedByUi = true;
        mutate(s -> {
            boolean sameConversation = Objects.equals(s.selectedConversationId, conversationId);
            s.screen = new NativeScreen(conversationId, title == null ? "" : title);
            s.selectedConversationId = conversationId;
            if (!sameConversation) {
                s.draft = "";
                s.retryDraft = null;
                s.sendStatus = SendStatus.IDLE;
            }
            s.error = null;
        });
        executor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<ChatMessage> messages = repository.getMessages(conversationId);
                    mutate(s -> s.messagesByConversation.put(conversationId, new ArrayList<>(messages)));
                    persistAsync();
                } catch (Exception error) {
                    mutate(s -> s.error = "消息加载失败，已展示本地缓存。");
                }
            }
        });
    }

    // endregion

    // region 输入与发送

    public void updateDraft(String text) {
        final String nextDraft = text == null ? "" : text;
        mutate(s -> {
            s.draft = nextDraft;
            if (s.retryDraft != null && !Objects.equals(s.retryDraft.text, nextDraft.trim())) {
                s.retryDraft = null;
            }
            if (s.sendStatus == SendStatus.FAILED || s.sendStatus == SendStatus.CANCELED) {
                s.sendStatus = SendStatus.IDLE;
                s.error = null;
            }
        });
    }

    public void applyComposerTemplate(String template) {
        final String nextTemplate = template == null ? "" : template.trim();
        if (nextTemplate.isEmpty()) {
            return;
        }
        mutate(s -> {
            String currentDraft = s.draft == null ? "" : trimEnd(s.draft);
            s.draft = currentDraft.trim().isEmpty() ? nextTemplate : currentDraft + "\n" + nextTemplate;
            s.retryDraft = null;
            if (!s.isStreaming()) {
                s.sendStatus = SendStatus.IDLE;
            }
            s.error = null;
            s.notice = "已插入快捷提示。";
        });
    }

    public void useMessageAsDraft(String content) {
        final String nextDraft = content == null ? "" : content.trim();
        if (nextDraft.isEmpty()) {
            showNotice("这条回复没有可编辑内容。");
            return;
        }
        mutate(s -> {
            s.draft = nextDraft;
            s.retryDraft = null;
            if (!s.isStreaming()) {
                s.sendStatus = SendStatus.IDLE;
            }
            s.error = null;
            s.notice = "回复内容已放入输入框。";
        });
    }

    public void sendMessage() {
        sendMessage(snapshot().draft);
    }

    public synchronized void sendMessage(String text) {
        final String prompt = text == null ? "" : text.trim();
        QianwenUiState snapshot = snapshot();
        final String conversationId = snapshot.selectedConversationId;
        if (prompt.isEmpty() || conversationId == null || snapshot.isStreaming()) {
            return;
        }

        sendCanceled = false;
        clearDeltaBuffer();
        synchronized (deltaLock) {
            lastDeltaTimestamp = 0L;
            emaIntervalMs = FLUSH_DELAY_MS;
        }

        mutate(s -> {
            s.draft = prompt;
            s.sendStatus = SendStatus.STREAMING;
            s.retryDraft = null;
            s.error = null;
            s.notice = null;
        });

        sendFuture = executor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    repository.streamChat(conversationId, prompt, new StreamCallback(conversationId));
                    if (sendCanceled) {
                        return;
                    }
                    if (snapshot().sendStatus == SendStatus.FAILED) {
                        persistAsync();
                        return;
                    }
                    mutate(s -> {
                        s.draft = "";
                        s.sendStatus = SendStatus.IDLE;
                        s.retryDraft = null;
                        s.error = null;
                        s.notice = "回复已完成。";
                    });
                    persistAsync();
                } catch (Exception error) {
                    if (sendCanceled) {
                        return;
                    }
                    final String message = error.getMessage() == null ? "未知网络错误" : error.getMessage();
                    mutate(s -> {
                        s.draft = prompt;
                        s.sendStatus = SendStatus.FAILED;
                        s.retryDraft = new RetryDraft(conversationId, prompt, message);
                        s.error = "发送失败：" + message;
                        s.notice = "发送失败，可点击重试。";
                    });
                    persistAsync();
                } finally {
                    sendFuture = null;
                }
            }
        });
    }

    public void retryLastMessage() {
        QianwenUiState snapshot = snapshot();
        final RetryDraft retry = snapshot.retryDraft;
        if (retry == null) {
            return;
        }
        mutate(s -> {
            s.selectedConversationId = retry.conversationId;
            s.draft = retry.text;
            s.error = null;
            s.notice = "正在重试上一条消息。";
        });
        sendMessage(retry.text);
    }

    public void regenerateFromMessage(ChatMessage message) {
        if (message == null) {
            return;
        }
        QianwenUiState snapshot = snapshot();
        if (snapshot.isStreaming()) {
            showNotice("生成中不可重新生成，请先取消。");
            return;
        }

        List<ChatMessage> messages = snapshot.messagesByConversation.get(message.conversationId);
        if (messages == null) {
            messages = Collections.emptyList();
        }
        int messageIndex = -1;
        for (int i = 0; i < messages.size(); i++) {
            if (Objects.equals(messages.get(i).id, message.id)) {
                messageIndex = i;
                break;
            }
        }

        String prompt = null;
        for (int i = (messageIndex < 0 ? messages.size() : messageIndex) - 1; i >= 0; i--) {
            ChatMessage candidate = messages.get(i);
            if ("user".equals(candidate.role) && candidate.content != null && !candidate.content.trim().isEmpty()) {
                prompt = candidate.content.trim();
                break;
            }
        }

        if (prompt == null || prompt.isEmpty()) {
            showNotice("没有找到可重新生成的上一条问题。");
            return;
        }

        final String finalPrompt = prompt;
        mutate(s -> {
            s.selectedConversationId = message.conversationId;
            s.draft = finalPrompt;
            s.retryDraft = null;
            s.error = null;
            s.notice = "正在重新生成回复。";
        });
        sendMessage(finalPrompt);
    }

    public synchronized void cancelSending() {
        QianwenUiState snapshot = snapshot();
        final String conversationId = snapshot.selectedConversationId;
        if (conversationId == null) {
            return;
        }
        final String prompt = snapshot.draft == null ? "" : snapshot.draft.trim();

        sendCanceled = true;
        mutate(s -> {
            s.sendStatus = SendStatus.CANCELED;
            if (!prompt.isEmpty()) {
                s.retryDraft = new RetryDraft(conversationId, prompt, "用户取消了本次生成。");
            }
            s.error = "已取消本次生成，可继续编辑后重新发送。";
            s.notice = "已取消生成。";
        });
        clearDeltaBuffer();
        repository.cancelStream();
        Future<?> future = sendFuture;
        if (future != null) {
            future.cancel(true);
        }
        persistAsync();
    }

    public void clearMessages() {
        QianwenUiState snapshot = snapshot();
        final String conversationId = snapshot.selectedConversationId;
        if (conversationId == null) {
            showNotice("请先选择会话。");
            return;
        }
        if (snapshot.isStreaming()) {
            showNotice("生成中不可清空，请先取消。");
            return;
        }
        executor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<ChatMessage> messages = repository.clearMessages(conversationId);
                    mutate(s -> {
                        s.messagesByConversation.put(conversationId, new ArrayList<>(messages));
                        s.error = null;
                        s.notice = "会话已清空。";
                    });
                    persistAsync();
                } catch (Exception error) {
                    mutate(s -> s.error = "清空失败，请稍后重试。");
                }
            }
        });
    }

    // endregion

    private final class StreamCallback implements QianwenApiClientJava.ChatEventCallback {
        private final String conversationId;

        StreamCallback(String conversationId) {
            this.conversationId = conversationId;
        }

        @Override
        public void onEvent(ChatStreamEvent event) {
            if (sendCanceled) {
                return;
            }
            if (event.isDelta()) {
                if (event.messageId == null) {
                    return;
                }
                synchronized (deltaLock) {
                    DeltaAccum accum = deltaBuffer.get(event.messageId);
                    if (accum == null) {
                        accum = new DeltaAccum(conversationId, event.messageId);
                        deltaBuffer.put(event.messageId, accum);
                    }
                    accum.sb.append(event.delta == null ? "" : event.delta);
                }
                recordDeltaTiming();
                scheduleFlush();
                return;
            }

            flushDeltas();
            if (sendCanceled) {
                return;
            }
            final ChatStreamEvent streamEvent = event;
            mutate(s -> applyStreamEvent(s, conversationId, streamEvent));
            if (event.isError()) {
                persistAsync();
            }
        }

        @Override
        public void onError(Exception error) {
            if (sendCanceled) {
                return;
            }
            // 连接异常时先把已到达的增量落屏，避免最新一段内容丢失。
            flushDeltas();
            final String message = error == null || error.getMessage() == null
                    ? "未知网络错误"
                    : error.getMessage();
            mutate(s -> {
                s.sendStatus = SendStatus.FAILED;
                s.retryDraft = new RetryDraft(conversationId, s.draft == null ? "" : s.draft, message);
                s.error = "发送失败：" + message;
                s.notice = "发送失败，可点击重试。";
            });
            persistAsync();
        }
    }

    private void flushDeltas() {
        List<DeltaAccum> drained = new ArrayList<>();
        synchronized (deltaLock) {
            if (deltaBuffer.isEmpty()) {
                return;
            }
            for (String messageId : new ArrayList<>(deltaBuffer.keySet())) {
                DeltaAccum accum = deltaBuffer.remove(messageId);
                if (accum != null) {
                    drained.add(accum);
                }
            }
        }
        if (drained.isEmpty()) {
            return;
        }
        mutate(s -> {
            for (DeltaAccum accum : drained) {
                String text = accum.sb.toString();
                if (text.isEmpty()) {
                    continue;
                }
                appendDelta(s, accum.conversationId, accum.messageId, text);
            }
        });
    }

    private void clearDeltaBuffer() {
        synchronized (deltaLock) {
            deltaBuffer.clear();
        }
        mainHandler.removeCallbacks(flushRunnable);
    }

    private void recordDeltaTiming() {
        long now = System.currentTimeMillis();
        synchronized (deltaLock) {
            if (lastDeltaTimestamp > 0) {
                double interval = (double) (now - lastDeltaTimestamp);
                emaIntervalMs = EMA_ALPHA * interval + (1 - EMA_ALPHA) * emaIntervalMs;
                emaIntervalMs = Math.max(MIN_DELAY_MS, Math.min(MAX_DELAY_MS, emaIntervalMs));
            }
            lastDeltaTimestamp = now;
        }
    }

    private void scheduleFlush() {
        mainHandler.removeCallbacks(flushRunnable);
        mainHandler.postDelayed(flushRunnable, computeAdaptiveDelay());
    }

    private int computeAdaptiveDelay() {
        double delay;
        synchronized (deltaLock) {
            delay = emaIntervalMs;
        }
        return (int) Math.round(Math.max(MIN_DELAY_MS, Math.min(MAX_DELAY_MS, delay)));
    }

    private void applyStreamEvent(QianwenUiState state, String requestedConversationId, ChatStreamEvent event) {
        String type = event.type == null ? "" : event.type;
        switch (type) {
            case ChatStreamEvent.TYPE_CONVERSATION:
                if (event.conversation == null) {
                    return;
                }
                state.conversations = sortConversations(upsertConversation(state.conversations, event.conversation));
                state.selectedConversationId = event.conversation.id;
                state.screen = syncScreenTitle(state.screen, Collections.singletonList(event.conversation));
                break;
            case ChatStreamEvent.TYPE_MESSAGE:
            case ChatStreamEvent.TYPE_DONE:
                upsertMessage(state, event.message);
                break;
            case ChatStreamEvent.TYPE_DELTA:
                appendDelta(state,
                        event.conversationId == null ? requestedConversationId : event.conversationId,
                        event.messageId,
                        event.delta == null ? "" : event.delta);
                break;
            case ChatStreamEvent.TYPE_NEWS:
                applyNewsEvent(state, requestedConversationId, event);
                break;
            case ChatStreamEvent.TYPE_ERROR:
                applyStreamError(state, requestedConversationId, event);
                break;
            default:
                break;
        }
    }

    private void applyStreamError(QianwenUiState state, String requestedConversationId, ChatStreamEvent event) {
        String conversationId = event.conversationId == null ? requestedConversationId : event.conversationId;
        if (event.messageId != null) {
            List<ChatMessage> messages = new ArrayList<>(messagesOf(state, conversationId));
            for (int i = 0; i < messages.size(); i++) {
                ChatMessage message = messages.get(i);
                if (Objects.equals(message.id, event.messageId)) {
                    ChatMessage copy = copyMessage(message);
                    copy.status = "error";
                    copy.error = event.error;
                    messages.set(i, copy);
                }
            }
            state.messagesByConversation.put(conversationId, messages);
        }
        state.sendStatus = SendStatus.FAILED;
        state.retryDraft = new RetryDraft(conversationId, state.draft == null ? "" : state.draft, event.error);
        state.error = event.error;
        state.notice = "流式回复失败，可点击重试。";
    }

    /**
     * 新闻列表消息：服务端以 news 事件下发一批新闻，这里生成一条独立的助手消息追加到会话末尾。
     * 事件带 messageId 时按 id 覆盖，重试或重发不会产生重复卡片。
     */
    private void applyNewsEvent(QianwenUiState state, String requestedConversationId, ChatStreamEvent event) {
        if (event.news == null || event.news.isEmpty()) {
            return;
        }
        String conversationId = event.conversationId == null ? requestedConversationId : event.conversationId;
        if (conversationId == null) {
            return;
        }
        List<NewsItem> items = new ArrayList<>();
        for (NewsItem item : event.news) {
            if (item != null) {
                items.add(item);
            }
        }
        if (items.isEmpty()) {
            return;
        }

        ChatMessage message = new ChatMessage();
        message.id = event.messageId == null ? "news-" + UUID.randomUUID() : event.messageId;
        message.conversationId = conversationId;
        message.role = ChatMessage.ROLE_ASSISTANT;
        message.type = ChatMessage.TYPE_NEWS;
        message.content = "";
        message.status = "sent";
        message.createdAt = Instant.now().toString();
        message.updatedAt = message.createdAt;
        message.news = items;
        upsertMessage(state, message);
    }

    private void appendDelta(QianwenUiState state, String conversationId, String messageId, String delta) {
        if (messageId == null) {
            return;
        }
        List<ChatMessage> messages = new ArrayList<>(messagesOf(state, conversationId));
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            if (Objects.equals(message.id, messageId)) {
                ChatMessage copy = copyMessage(message);
                copy.content = (message.content == null ? "" : message.content) + delta;
                copy.status = "streaming";
                messages.set(i, copy);
                state.messagesByConversation.put(conversationId, messages);
                return;
            }
        }
        ChatMessage placeholder = new ChatMessage();
        placeholder.id = messageId;
        placeholder.conversationId = conversationId;
        placeholder.role = "assistant";
        placeholder.content = delta;
        placeholder.status = "streaming";
        messages.add(placeholder);
        state.messagesByConversation.put(conversationId, messages);
    }

    private void upsertMessage(QianwenUiState state, ChatMessage message) {
        if (message == null) {
            return;
        }
        String conversationId = message.conversationId == null
                ? state.selectedConversationId
                : message.conversationId;
        if (conversationId == null) {
            return;
        }
        state.messagesByConversation.put(conversationId, upsertMessage(messagesOf(state, conversationId), message));
    }

    private void restoreLocalSnapshot() {
        LocalSnapshot.ReadResult result = repository.readSnapshot();
        LocalSnapshot snapshot = result.snapshot;
        List<Conversation> conversations = sortConversations(snapshot.conversations);
        String selectedConversationId = null;
        for (Conversation conversation : conversations) {
            if (Objects.equals(conversation.id, snapshot.selectedConversationId)) {
                selectedConversationId = conversation.id;
                break;
            }
        }

        NativeScreen restoredScreen = NativeScreen.CONVERSATIONS;
        for (Conversation conversation : conversations) {
            if (Objects.equals(conversation.id, selectedConversationId)) {
                restoredScreen = new NativeScreen(conversation.id, conversation.title);
                break;
            }
        }

        final List<Conversation> restoredConversations = conversations;
        final String restoredSelectedId = selectedConversationId;
        final NativeScreen restored = restoredScreen;
        mutate(s -> {
            if (!screenRequestedByUi) {
                s.screen = restored;
                s.selectedConversationId = restoredSelectedId;
            }
            s.conversations = restoredConversations;
            s.messagesByConversation = snapshot.messagesByConversation == null
                    ? new HashMap<>()
                    : new HashMap<>(snapshot.messagesByConversation);
            s.listStatus = restoredConversations.isEmpty()
                    ? ConversationListStatus.LOADING
                    : ConversationListStatus.READY;
            s.cacheStatus = toCacheStatus(result.status);
            s.lastCacheSavedAt = snapshot.savedAt;
            if (result.status == LocalSnapshot.Status.CORRUPTED) {
                s.error = "本地缓存解析失败，已使用空状态继续。";
            }
        });
    }

    private void persistAsync() {
        executor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    QianwenUiState source = snapshot();
                    final String savedAt = Instant.now().toString();
                    LocalSnapshot snapshot = new LocalSnapshot();
                    snapshot.version = LocalSnapshot.VERSION;
                    snapshot.savedAt = savedAt;
                    snapshot.conversations = new ArrayList<>(source.conversations);
                    snapshot.messagesByConversation = new HashMap<>(source.messagesByConversation);
                    snapshot.selectedConversationId = source.selectedConversationId;
                    repository.writeSnapshot(snapshot);
                    mutate(s -> {
                        s.cacheStatus = CacheStatus.SAVED;
                        s.lastCacheSavedAt = savedAt;
                    });
                } catch (Exception error) {
                    mutate(s -> {
                        s.cacheStatus = CacheStatus.CORRUPTED;
                        s.error = "本地缓存写入失败，远端会话不受影响。";
                    });
                }
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        clearDeltaBuffer();
        executor.shutdownNow();
        repository.shutdown();
        mainHandler.removeCallbacksAndMessages(null);
    }

    private static List<ChatMessage> messagesOf(QianwenUiState state, String conversationId) {
        List<ChatMessage> messages = state.messagesByConversation.get(conversationId);
        return messages == null ? Collections.<ChatMessage>emptyList() : messages;
    }

    private static List<ChatMessage> upsertMessage(List<ChatMessage> messages, ChatMessage message) {
        List<ChatMessage> next = new ArrayList<>();
        for (ChatMessage item : messages) {
            if (!Objects.equals(item.id, message.id)) {
                next.add(item);
            }
        }
        next.add(message);
        return next;
    }

    private static ChatMessage copyMessage(ChatMessage source) {
        ChatMessage copy = new ChatMessage();
        copy.id = source.id;
        copy.conversationId = source.conversationId;
        copy.role = source.role;
        copy.content = source.content;
        copy.status = source.status;
        copy.createdAt = source.createdAt;
        copy.updatedAt = source.updatedAt;
        copy.error = source.error;
        return copy;
    }

    private static List<Conversation> upsertConversation(List<Conversation> conversations, Conversation conversation) {
        List<Conversation> next = new ArrayList<>();
        next.add(conversation);
        for (Conversation item : conversations) {
            if (!Objects.equals(item.id, conversation.id)) {
                next.add(item);
            }
        }
        return next;
    }

    private static List<Conversation> mergeConversations(List<Conversation> remote, List<Conversation> cached) {
        List<Conversation> merged = new ArrayList<>();
        if (remote != null) {
            merged.addAll(remote);
        }
        if (cached != null) {
            merged.addAll(cached);
        }
        List<Conversation> distinct = new ArrayList<>();
        for (Conversation conversation : merged) {
            boolean exists = false;
            for (Conversation item : distinct) {
                if (Objects.equals(item.id, conversation.id)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                distinct.add(conversation);
            }
        }
        return sortConversations(distinct);
    }

    private static List<Conversation> sortConversations(List<Conversation> conversations) {
        List<Conversation> sorted = new ArrayList<>(conversations);
        Collections.sort(sorted, new Comparator<Conversation>() {
            @Override
            public int compare(Conversation left, Conversation right) {
                if (left.pinned != right.pinned) {
                    return left.pinned ? -1 : 1;
                }
                String leftUpdated = left.updatedAt == null ? "" : left.updatedAt;
                String rightUpdated = right.updatedAt == null ? "" : right.updatedAt;
                return rightUpdated.compareTo(leftUpdated);
            }
        });
        return sorted;
    }

    private static ConversationListStatus toListStatus(List<Conversation> conversations) {
        return conversations.isEmpty() ? ConversationListStatus.EMPTY : ConversationListStatus.READY;
    }

    private static CacheStatus toCacheStatus(LocalSnapshot.Status status) {
        switch (status) {
            case RESTORED:
                return CacheStatus.RESTORED;
            case CORRUPTED:
                return CacheStatus.CORRUPTED;
            case EMPTY:
            default:
                return CacheStatus.EMPTY;
        }
    }

    private static NativeScreen syncScreenTitle(NativeScreen screen, List<Conversation> conversations) {
        if (!screen.isChat()) {
            return screen;
        }
        for (Conversation conversation : conversations) {
            if (Objects.equals(conversation.id, screen.conversationId)) {
                return new NativeScreen(conversation.id, conversation.title);
            }
        }
        return screen;
    }

    private static String trimEnd(String value) {
        int end = value.length();
        while (end > 0 && Character.isWhitespace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }
}
