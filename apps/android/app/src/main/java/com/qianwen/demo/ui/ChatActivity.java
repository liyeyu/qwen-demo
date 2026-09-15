package com.qianwen.demo.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.qianwen.demo.QianwenConfig;
import com.qianwen.demo.R;
import com.qianwen.demo.data.ChatMessage;
import com.qianwen.demo.data.NativeScreen;
import com.qianwen.demo.data.NewsItem;
import com.qianwen.demo.ui.QianwenUiState.SendStatus;
import java.util.Collections;
import java.util.List;

public class ChatActivity extends AppCompatActivity {
    public static final String EXTRA_CONV_ID = "conv_id";
    public static final String EXTRA_TITLE = "title";

    private QianwenViewModelJava viewModel;
    private MessagesAdapter adapter;
    private RecyclerView recycler;
    private LinearLayoutManager layoutManager;
    private EditText input;
    private Button sendButton;
    private Button cancelButton;
    private Button retryButton;
    private TextView titleText;
    private TextView subtitleText;
    private TextView sendStateText;
    private TextView errorText;
    private TextView noticeText;
    private String conversationTitle = "";
    private boolean updatingInput = false;
    private boolean sawChatScreen = false;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable scrollRunnable = new Runnable() {
        @Override
        public void run() {
            scrollToBottomIfNeeded();
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        recycler = findViewById(R.id.messages_recycler);
        input = findViewById(R.id.input);
        sendButton = findViewById(R.id.send_button);
        cancelButton = findViewById(R.id.cancel_button);
        retryButton = findViewById(R.id.retry_button);
        titleText = findViewById(R.id.title_text);
        subtitleText = findViewById(R.id.subtitle_text);
        sendStateText = findViewById(R.id.send_state_text);
        errorText = findViewById(R.id.error_text);
        noticeText = findViewById(R.id.notice_text);

        adapter = new MessagesAdapter(new MessagesAdapter.Listener() {
            @Override
            public void onEdit(ChatMessage message) {
                viewModel.useMessageAsDraft(message.content);
            }

            @Override
            public void onRegenerate(ChatMessage message) {
                viewModel.regenerateFromMessage(message);
            }

            @Override
            public void onNewsClick(NewsItem item) {
                openNews(item);
            }
        });
        layoutManager = new LinearLayoutManager(this);
        recycler.setLayoutManager(layoutManager);
        recycler.setAdapter(adapter);

        final String conversationId = getIntent().getStringExtra(EXTRA_CONV_ID);
        conversationTitle = getIntent().getStringExtra(EXTRA_TITLE) == null
                ? ""
                : getIntent().getStringExtra(EXTRA_TITLE);

        viewModel = new ViewModelProvider(this, QianwenViewModelJava.factory(getApplication()))
                .get(QianwenViewModelJava.class);

        if (conversationId != null) {
            viewModel.navigate(new NativeScreen(conversationId, conversationTitle));
        }

        viewModel.state.observe(this, this::render);

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!updatingInput) {
                    viewModel.updateDraft(s == null ? "" : s.toString());
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                viewModel.sendMessage();
                uiHandler.postDelayed(scrollRunnable, 200);
            }
        });
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                viewModel.cancelSending();
            }
        });
        retryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                viewModel.retryLastMessage();
            }
        });

        findViewById(R.id.think_button).setOnClickListener(v -> viewModel.applyComposerTemplate("请一步一步思考："));
        findViewById(R.id.work_button).setOnClickListener(v -> viewModel.applyComposerTemplate("请给出可执行办事方案："));
        findViewById(R.id.image_button).setOnClickListener(v -> viewModel.applyComposerTemplate("请帮我写一段 AI 生图提示词："));
        findViewById(R.id.photo_button).setOnClickListener(v -> viewModel.applyComposerTemplate("请根据这道题逐步讲解："));
        findViewById(R.id.clear_button).setOnClickListener(v -> viewModel.clearMessages());
        findViewById(R.id.back_button).setOnClickListener(v -> exitToConversations());
        findViewById(R.id.status_button).setOnClickListener(v -> showStatusDialog());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                exitToConversations();
            }
        });
    }

    /** 点击新闻卡片：有链接就交给系统打开，否则给个提示。 */
    private void openNews(NewsItem item) {
        String url = item == null || item.url == null ? "" : item.url.trim();
        if (url.isEmpty()) {
            viewModel.showNotice("这条新闻没有可打开的链接。");
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException error) {
            viewModel.showNotice("没有可打开该链接的应用。");
        }
    }

    private void exitToConversations() {
        viewModel.navigate(NativeScreen.CONVERSATIONS);
        finish();
    }

    private void render(QianwenUiState state) {
        if (state == null) {
            return;
        }
        if (state.screen.isChat()) {
            sawChatScreen = true;
            conversationTitle = state.screen.title;
        } else if (sawChatScreen) {
            finish();
            return;
        }

        titleText.setText("千问");
        subtitleText.setText(conversationTitle);

        List<ChatMessage> messages = state.messagesByConversation.get(state.selectedConversationId);
        adapter.setItems(messages == null ? Collections.<ChatMessage>emptyList() : messages);
        autoScrollToBottom();

        String draft = state.draft == null ? "" : state.draft;
        if (!draft.equals(input.getText().toString())) {
            updatingInput = true;
            input.setText(draft);
            input.setSelection(draft.length());
            updatingInput = false;
        }

        boolean streaming = state.isStreaming();
        cancelButton.setVisibility(streaming ? View.VISIBLE : View.GONE);
        retryButton.setVisibility(!streaming && state.retryDraft != null ? View.VISIBLE : View.GONE);
        boolean canSend = !streaming
                && state.selectedConversationId != null
                && !draft.trim().isEmpty();
        sendButton.setEnabled(canSend);

        String sendState = sendStatusText(state.sendStatus);
        if (sendState == null) {
            sendStateText.setVisibility(View.GONE);
        } else {
            sendStateText.setVisibility(View.VISIBLE);
            sendStateText.setText(sendState);
        }
        setMessageText(errorText, state.error);
        setMessageText(noticeText, state.notice);
    }

    private void autoScrollToBottom() {
        boolean atBottom = true;
        int count = adapter.getItemCount();
        if (count > 0 && layoutManager != null) {
            // 只有最后一条消息完全可见时才自动滚动，避免打断用户向上翻阅。
            atBottom = layoutManager.findLastCompletelyVisibleItemPosition() >= count - 1;
        }
        if (atBottom) {
            uiHandler.removeCallbacks(scrollRunnable);
            uiHandler.postDelayed(scrollRunnable, 120);
        }
    }

    private void scrollToBottomIfNeeded() {
        if (recycler == null || adapter == null) {
            return;
        }
        int count = adapter.getItemCount();
        if (count <= 0) {
            return;
        }
        recycler.scrollToPosition(count - 1);
    }

    private void showStatusDialog() {
        QianwenUiState state = viewModel.state.getValue();
        if (state == null) {
            return;
        }
        String message = "status：" + value(state.health == null ? null : state.health.status) + "\n"
                + "modelMode：" + value(state.health == null ? null : state.health.modelMode) + "\n"
                + "timestamp：" + value(state.health == null ? null : state.health.timestamp) + "\n"
                + "lastCheck：" + value(state.lastHealthCheckedAt) + "\n"
                + "cache：" + state.cacheStatus.label + "\n"
                + "cacheSavedAt：" + value(state.lastCacheSavedAt) + "\n"
                + "api：" + QianwenConfig.getBaseUrl();
        new AlertDialog.Builder(this)
                .setTitle("服务状态")
                .setMessage(message)
                .setPositiveButton("重新检查", (dialog, which) -> viewModel.refreshHealth(true))
                .setNegativeButton("关闭", null)
                .show();
    }

    private static String sendStatusText(SendStatus status) {
        switch (status) {
            case STREAMING:
                return "正在接收 SSE 增量回复，可取消本次生成。";
            case FAILED:
                return "发送失败，输入已保留，可点击重试。";
            case CANCELED:
                return "已取消生成，输入仍保留。";
            case IDLE:
            default:
                return null;
        }
    }

    private static void setMessageText(TextView view, String message) {
        if (message == null || message.trim().isEmpty()) {
            view.setVisibility(View.GONE);
        } else {
            view.setVisibility(View.VISIBLE);
            view.setText(message);
        }
    }

    private static String value(String raw) {
        return raw == null || raw.isEmpty() ? "-" : raw;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        uiHandler.removeCallbacksAndMessages(null);
    }
}
