package com.qianwen.demo.ui;

import android.os.Handler;
import android.os.Looper;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.qianwen.demo.data.ChatMessage;
import com.qianwen.demo.data.ChatStreamEvent;
import com.qianwen.demo.data.StreamDeltaEvent;
import com.qianwen.demo.data.StreamDoneEvent;
import com.qianwen.demo.data.StreamErrorEvent;
import com.qianwen.demo.data.StreamMessageEvent;
import com.qianwen.demo.data.LocalSnapshotResult;
import com.qianwen.demo.data.QianwenRepositoryJava;
import com.qianwen.demo.data.SnapshotReadStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class QianwenViewModelJava extends ViewModel {
    private final QianwenRepositoryJava repository;
    private final MutableLiveData<QianwenUiState> _state = new MutableLiveData<>(new QianwenUiState());
    public LiveData<QianwenUiState> state = _state;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Future<?> sendFuture = null;

    public QianwenViewModelJava(QianwenRepositoryJava repository, String configuredApiBaseUrl) {
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

    public QianwenViewModelJava(QianwenRepositoryJava repository) {
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
            LocalSnapshotResult res = repository.readSnapshot();
            if (res != null && SnapshotReadStatus.Restored.equals(res.status)) {
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
            s.lastHealthCheckedAt = Instant.now().toString();
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

    private class QianwenApiEventAdapter implements com.qianwen.demo.data.QianwenApiClientJava.ChatEventCallback {
        private final String conversationId;
        public QianwenApiEventAdapter(String conversationId) {
            this.conversationId = conversationId;
        }

        @Override
        public void onEvent(ChatStreamEvent event) {
            if (event instanceof StreamDeltaEvent) {
                StreamDeltaEvent d = (StreamDeltaEvent) event;
                // append delta to last assistant message
                mainHandler.post(() -> {
                    QianwenUiState s = currentStateCopy();
                    List<ChatMessage> msgs = s.messagesByConversation.get(conversationId);
                    if (msgs == null) msgs = new ArrayList<>();
                    ChatMessage assistant = null;
                    if (!msgs.isEmpty()) {
                        ChatMessage last = msgs.get(msgs.size() - 1);
                        if ("assistant".equals(last.role)) {
                            assistant = last;
                        }
                    }
                    if (assistant == null) {
                        assistant = new ChatMessage();
                        assistant.id = java.util.UUID.randomUUID().toString();
                        assistant.conversationId = conversationId;
                        assistant.role = "assistant";
                        assistant.content = "";
                        assistant.status = "streaming";
                        msgs.add(assistant);
                    }
                    assistant.content = (assistant.content == null ? "" : assistant.content) + d.delta;
                    s.messagesByConversation.put(conversationId, msgs);
                    s.sendStatus = SendStatus.STREAMING;
                    _state.setValue(s);
                });
            } else if (event instanceof StreamMessageEvent) {
                // full message received
                StreamMessageEvent me = (StreamMessageEvent) event;
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
            } else if (event instanceof StreamDoneEvent) {
                StreamDoneEvent de = (StreamDoneEvent) event;
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
            } else if (event instanceof StreamErrorEvent) {
                StreamErrorEvent er = (StreamErrorEvent) event;
                mainHandler.post(() -> {
                    QianwenUiState s = currentStateCopy();
                    s.sendStatus = SendStatus.FAILED;
                    s.retryDraft = new RetryDraft(conversationId, s.draft == null ? "" : s.draft, er.error == null ? "stream error" : er.error);
                    s.draft = rawTrimmed(s.draft);
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

        private String rawTrimmed(String t) {
            if (t == null) return "";
            return t.trim();
        }
    }

    public synchronized void cancelSending() {
        if (sendFuture != null) {
            sendFuture.cancel(true);
            sendFuture = null;
        }
        QianwenUiState s = currentStateCopy();
        s.sendStatus = SendStatus.CANCELED;
        setStateOnMain(s);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdownNow();
        repository.shutdown();
    }
}
