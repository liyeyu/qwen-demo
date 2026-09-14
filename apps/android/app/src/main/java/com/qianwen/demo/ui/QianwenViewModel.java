package com.qianwen.demo.ui;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import com.qianwen.demo.data.ChatMessage;
import com.qianwen.demo.data.QianwenRepositoryJava;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class QianwenViewModel extends androidx.lifecycle.ViewModel {
    private final QianwenRepositoryJava repository;
    private final androidx.lifecycle.MutableLiveData<QianwenUiState> _state = new androidx.lifecycle.MutableLiveData<>(new QianwenUiState());
    public androidx.lifecycle.LiveData<QianwenUiState> state = _state;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Future<?> sendFuture = null;

    // delta buffer: messageId -> DeltaAccum
    private final Map<String, DeltaAccum> deltaBuffer = new ConcurrentHashMap<>();
    private final Runnable flushRunnable = this::flushDeltas;
    private static final int FLUSH_DELAY_MS = 120;

    // adaptive delay fields
    private long lastDeltaTimestamp = 0L;
    private double emaIntervalMs = FLUSH_DELAY_MS; // exponential moving average of intervals
    private static final double EMA_ALPHA = 0.3;
    private static final int MIN_DELAY_MS = 40;
    private static final int MAX_DELAY_MS = 300;

    public QianwenViewModel(QianwenRepositoryJava repository, String configuredApiBaseUrl) {
        this.repository = repository;
        QianwenUiState s = _state.getValue();
        if (s == null) s = new QianwenUiState();
        s.apiBaseUrl = configuredApiBaseUrl;
        _state.setValue(s);

        // initial actions
        executor.submit(() -> {
            restoreLocalSnapshot();
            refreshHealth();
            refreshConversations();
        });
    }

    public QianwenViewModel(QianwenRepositoryJava repository) {
        this(repository, com.qianwen.demo.BuildConfig.QWEN_API_BASE_URL);
    }

    private void postState(QianwenUiState s) {
        _state.postValue(s);
    }

    private void setStateOnMain(QianwenUiState s) {
        mainHandler.post(() -> _state.setValue(s));
    }

    private QianwenUiState currentStateCopy() {
        QianwenUiState cur = _state.getValue();
        if (cur == null) cur = new QianwenUiState();
        return new QianwenUiState(cur);
    }

    private void restoreLocalSnapshot() {
        try {
            com.qianwen.demo.data.LocalSnapshotResult res = repository.readSnapshot();
            if (res != null && com.qianwen.demo.data.SnapshotReadStatus.Restored.equals(res.status)) {
                QianwenUiState s = currentStateCopy();
                s.cacheStatus = CacheStatus.RESTORED;
                s.selectedConversationId = res.snapshot.selectedConversationId;
                s.messagesByConversation = res.snapshot.messagesByConversation;
                s.conversations = res.snapshot.conversations;
                setStateOnMain(s);
            } else {
                QianwenUiState s = currentStateCopy();
                s.cacheStatus = CacheStatus.EMPTY;
                setStateOnMain(s);
            }
        } catch (Exception e) {
            QianwenUiState s = currentStateCopy();
            s.cacheStatus = CacheStatus.CORRUPTED;
            s.error = e.getMessage();
            setStateOnMain(s);
        }
    }

    private void refreshHealth() {
        try {
            com.qianwen.demo.data.HealthResponse h = repository.health();
            QianwenUiState s = currentStateCopy();
            s.health = h;
            s.serviceStatus = ServiceStatus.ONLINE;
            s.lastHealthCheckedAt = java.time.Instant.now().toString();
            setStateOnMain(s);
        } catch (Exception e) {
            QianwenUiState s = currentStateCopy();
            s.serviceStatus = ServiceStatus.OFFLINE;
            s.error = e.getMessage();
            setStateOnMain(s);
        }
    }

    private void refreshConversations() {
        try {
            List<com.qianwen.demo.data.Conversation> list = repository.listConversations();
            QianwenUiState s = currentStateCopy();
            s.conversations = list;
            if (list == null || list.isEmpty()) {
                s.listStatus = ConversationListStatus.EMPTY;
            } else {
                s.listStatus = ConversationListStatus.READY;
            }
            setStateOnMain(s);
        } catch (Exception e) {
            QianwenUiState s = currentStateCopy();
            s.listStatus = ConversationListStatus.OFFLINE;
            s.error = e.getMessage();
            setStateOnMain(s);
        }
    }

    public void navigateToChat(String conversationId, String title) {
        QianwenUiState s = currentStateCopy();
        s.screen = new com.qianwen.demo.data.NativeScreen.Chat(conversationId, title);
        s.error = null;
        setStateOnMain(s);
        selectConversation(conversationId, title);
    }

    public void selectConversation(String conversationId, String title) {
        executor.submit(() -> {
            try {
                List<ChatMessage> messages = repository.getMessages(conversationId);
                QianwenUiState s = currentStateCopy();
                s.selectedConversationId = conversationId;
                s.messagesByConversation.put(conversationId, messages);
                setStateOnMain(s);
            } catch (Exception e) {
                QianwenUiState s = currentStateCopy();
                s.error = e.getMessage();
                setStateOnMain(s);
            }
        });
    }

    public void updateDraft(String text) {
        QianwenUiState s = currentStateCopy();
        s.draft = text;
        setStateOnMain(s);
    }

    public synchronized void sendMessage() {
        QianwenUiState cur = _state.getValue();
        if (cur == null) return;
        final String convId = cur.selectedConversationId;
        if (convId == null) return;
        final String raw = cur.draft == null ? "" : cur.draft.trim();
        if (raw.isEmpty()) return;
        if (cur.sendStatus == SendStatus.STREAMING) return;

        // prepare assistant message placeholder
        executor.submit(() -> {
            QianwenUiState s1 = currentStateCopy();
            s1.sendStatus = SendStatus.STREAMING;
            s1.draft = raw;
            // append user message
            ChatMessage user = new ChatMessage();
            user.id = java.util.UUID.randomUUID().toString();
            user.conversationId = convId;
            user.role = "user";
            user.content = raw;
            user.status = "sent";
            List<ChatMessage> list = s1.messagesByConversation.get(convId);
            if (list == null) list = new ArrayList<>();
            list.add(user);
            s1.messagesByConversation.put(convId, list);
            setStateOnMain(s1);

            // start streaming
            sendFuture = executor.submit(() -> {
                repository.streamChat(convId, raw, new QianwenApiEventAdapter(convId));
            });
        });
    }

    private void flushDeltas() {
        if (deltaBuffer.isEmpty()) return;
        // copy keys to avoid concurrent modification
        List<String> keys = new ArrayList<>(deltaBuffer.keySet());
        boolean changed = false;
        QianwenUiState s = currentStateCopy();
        for (String messageId : keys) {
            DeltaAccum acc = deltaBuffer.remove(messageId);
            if (acc == null) continue;
            String convId = acc.conversationId;
            String deltaText = acc.sb.toString();
            if (deltaText.isEmpty()) continue;
            List<ChatMessage> msgs = s.messagesByConversation.get(convId);
            if (msgs == null) msgs = new ArrayList<>();
            // find message by id
            ChatMessage found = null;
            for (int i = 0; i < msgs.size(); i++) {
                ChatMessage m = msgs.get(i);
                if (messageId != null && messageId.equals(m.id)) {
                    found = m;
                    break;
                }
            }
            if (found == null) {
                ChatMessage assistant = new ChatMessage();
                assistant.id = messageId == null ? java.util.UUID.randomUUID().toString() : messageId;
                assistant.conversationId = convId;
                assistant.role = "assistant";
                assistant.content = deltaText;
                assistant.status = "streaming";
                msgs.add(assistant);
            } else {
                found.content = (found.content == null ? "" : found.content) + deltaText;
                found.status = "streaming";
            }
            s.messagesByConversation.put(convId, msgs);
            s.sendStatus = SendStatus.STREAMING;
            changed = true;
        }
        if (changed) {
            setStateOnMain(s);
        }
    }

    private void scheduleFlush() {
        mainHandler.removeCallbacks(flushRunnable);
        int delay = computeAdaptiveDelay();
        mainHandler.postDelayed(flushRunnable, delay);
    }

    private int computeAdaptiveDelay() {
        int delay = (int) Math.round(Math.max(MIN_DELAY_MS, Math.min(MAX_DELAY_MS, emaIntervalMs)));
        return delay;
    }

    private void recordDeltaTiming() {
        long now = System.currentTimeMillis();
        if (lastDeltaTimestamp > 0) {
            double interval = (double) (now - lastDeltaTimestamp);
            emaIntervalMs = EMA_ALPHA * interval + (1 - EMA_ALPHA) * emaIntervalMs;
            // clamp emaIntervalMs
            if (emaIntervalMs < MIN_DELAY_MS) emaIntervalMs = MIN_DELAY_MS;
            if (emaIntervalMs > MAX_DELAY_MS) emaIntervalMs = MAX_DELAY_MS;
        }
        lastDeltaTimestamp = now;
    }

    private class QianwenApiEventAdapter implements com.qianwen.demo.data.QianwenApiClientJava.ChatEventCallback {
        private final String conversationId;
        public QianwenApiEventAdapter(String conversationId) {
            this.conversationId = conversationId;
        }

        @Override
        public void onEvent(com.qianwen.demo.data.ChatStreamEvent event) {
            if (event instanceof com.qianwen.demo.data.StreamDeltaEvent) {
                com.qianwen.demo.data.StreamDeltaEvent d = (com.qianwen.demo.data.StreamDeltaEvent) event;
                // buffer delta per message id and schedule flush
                String msgId = d.messageId == null ? java.util.UUID.randomUUID().toString() : d.messageId;
                DeltaAccum acc = deltaBuffer.computeIfAbsent(msgId, k -> new DeltaAccum(conversationId, msgId));
                synchronized (acc) {
                    acc.sb.append(d.delta == null ? "" : d.delta);
                }
                // record timing and schedule flush adaptively
                recordDeltaTiming();
                scheduleFlush();
            } else if (event instanceof com.qianwen.demo.data.StreamMessageEvent) {
                // full message received - flush any buffered deltas for this messageId then add message
                com.qianwen.demo.data.StreamMessageEvent me = (com.qianwen.demo.data.StreamMessageEvent) event;
                // flush buffer for message id
                if (me.message != null && me.message.id != null) {
                    DeltaAccum acc = deltaBuffer.remove(me.message.id);
                    if (acc != null && acc.sb.length() > 0) {
                        // apply accumulated delta before replacing with full message
                        QianwenUiState s = currentStateCopy();
                        List<ChatMessage> msgs = s.messagesByConversation.get(conversationId);
                        if (msgs == null) msgs = new ArrayList<>();
                        ChatMessage assistant = new ChatMessage();
                        assistant.id = acc.messageId;
                        assistant.conversationId = conversationId;
                        assistant.role = "assistant";
                        assistant.content = acc.sb.toString();
                        assistant.status = "streaming";
                        msgs.add(assistant);
                        s.messagesByConversation.put(conversationId, msgs);
                        setStateOnMain(s);
                    }
                }

                mainHandler.post(() -> {
                    QianwenUiState s = currentStateCopy();
                    List<ChatMessage> msgs = s.messagesByConversation.get(conversationId);
                    if (msgs == null) msgs = new ArrayList<>();
                    msgs.add(me.message);
                    s.messagesByConversation.put(conversationId, msgs);
                    s.sendStatus = SendStatus.IDLE;
                    s.draft = "";
                    _state.setValue(s);
                });
            } else if (event instanceof com.qianwen.demo.data.StreamDoneEvent) {
                com.qianwen.demo.data.StreamDoneEvent de = (com.qianwen.demo.data.StreamDoneEvent) event;
                // flush buffered deltas for this message id immediately
                if (de.message != null && de.message.id != null) {
                    DeltaAccum acc = deltaBuffer.remove(de.message.id);
                    if (acc != null && acc.sb.length() > 0) {
                        QianwenUiState s = currentStateCopy();
                        List<ChatMessage> msgs = s.messagesByConversation.get(conversationId);
                        if (msgs == null) msgs = new ArrayList<>();
                        ChatMessage assistant = new ChatMessage();
                        assistant.id = acc.messageId;
                        assistant.conversationId = conversationId;
                        assistant.role = "assistant";
                        assistant.content = acc.sb.toString();
                        assistant.status = "streaming";
                        msgs.add(assistant);
                        s.messagesByConversation.put(conversationId, msgs);
                        setStateOnMain(s);
                    }
                }

                mainHandler.post(() -> {
                    QianwenUiState s = currentStateCopy();
                    List<ChatMessage> msgs = s.messagesByConversation.get(conversationId);
                    if (msgs == null) msgs = new ArrayList<>();
                    msgs.add(de.message);
                    s.messagesByConversation.put(conversationId, msgs);
                    s.sendStatus = SendStatus.IDLE;
                    s.draft = "";
                    _state.setValue(s);
                });
            } else if (event instanceof com.qianwen.demo.data.StreamErrorEvent) {
                com.qianwen.demo.data.StreamErrorEvent er = (com.qianwen.demo.data.StreamErrorEvent) event;
                // flush and mark error
                if (er.messageId != null) {
                    DeltaAccum acc = deltaBuffer.remove(er.messageId);
                    // we still want to apply any accumulated content as partial
                    if (acc != null && acc.sb.length() > 0) {
                        QianwenUiState s = currentStateCopy();
                        List<ChatMessage> msgs = s.messagesByConversation.get(conversationId);
                        if (msgs == null) msgs = new ArrayList<>();
                        ChatMessage assistant = new ChatMessage();
                        assistant.id = acc.messageId;
                        assistant.conversationId = conversationId;
                        assistant.role = "assistant";
                        assistant.content = acc.sb.toString();
                        assistant.status = "error";
                        assistant.error = er.error;
                        msgs.add(assistant);
                        s.messagesByConversation.put(conversationId, msgs);
                        s.sendStatus = SendStatus.FAILED;
                        s.retryDraft = new RetryDraft(conversationId, s.draft == null ? "" : s.draft, er.error == null ? "stream error" : er.error);
                        s.error = er.error;
                        setStateOnMain(s);
                        return;
                    }
                }

                mainHandler.post(() -> {
                    QianwenUiState s = currentStateCopy();
                    s.sendStatus = SendStatus.FAILED;
                    s.retryDraft = new RetryDraft(conversationId, s.draft == null ? "" : s.draft, er.error == null ? "stream error" : er.error);
                    s.error = er.error;
                    _state.setValue(s);
                });
            }
        }

        @Override
        public void onError(Exception e) {
            mainHandler.post(() -> {
                QianwenUiState s = currentStateCopy();
                s.sendStatus = SendStatus.FAILED;
                s.retryDraft = new RetryDraft(conversationId, s.draft == null ? "" : s.draft, e.getMessage());
                _state.setValue(s);
            });
        }
    }

    public synchronized void cancelSending() {
        if (sendFuture != null) {
            sendFuture.cancel(true);
            sendFuture = null;
        }
        // clear pending deltas
        deltaBuffer.clear();
        QianwenUiState s = currentStateCopy();
        s.sendStatus = SendStatus.CANCELED;
        setStateOnMain(s);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdownNow();
        repository.shutdown();
        mainHandler.removeCallbacksAndMessages(null);
        deltaBuffer.clear();
    }

    public static ViewModelProvider.Factory factory(final Application application) {
        return new ViewModelProvider.Factory() {
            @SuppressWarnings("unchecked")
            @Override
            public <T extends androidx.lifecycle.ViewModel> T create(Class<T> modelClass) {
                if (modelClass.isAssignableFrom(QianwenViewModel.class)) {
                    QianwenRepositoryJava repo = new QianwenRepositoryJava(application);
                    return (T) new QianwenViewModel(repo);
                }
                throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
            }
        };
    }
}
