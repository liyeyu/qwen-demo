package com.qianwen.demo.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.qianwen.demo.QianwenConfig;
import com.qianwen.demo.R;
import com.qianwen.demo.data.Conversation;
import com.qianwen.demo.data.NativeScreen;
import com.qianwen.demo.ui.QianwenUiState.ConversationListStatus;
import java.util.ArrayList;
import java.util.List;

public class ConversationsActivity extends AppCompatActivity {
    private QianwenViewModelJava viewModel;
    private ConversationsAdapter adapter;
    private ProgressBar progressBar;
    private TextView errorText;
    private TextView noticeText;
    private TextView cacheText;
    private TextView serviceText;
    private EditText searchInput;
    private String launchedChatId = null;
    private boolean updatingSearchInput = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversations);

        progressBar = findViewById(R.id.progress);
        errorText = findViewById(R.id.error_text);
        noticeText = findViewById(R.id.notice_text);
        cacheText = findViewById(R.id.cache_text);
        serviceText = findViewById(R.id.service_text);
        searchInput = findViewById(R.id.search_input);
        Button createButton = findViewById(R.id.create_button);
        Button settingsButton = findViewById(R.id.settings_button);
        Button statusButton = findViewById(R.id.status_button);
        RecyclerView recycler = findViewById(R.id.recycler);

        adapter = new ConversationsAdapter(new ConversationsAdapter.Listener() {
            @Override
            public void onClick(Conversation conversation) {
                viewModel.navigate(new NativeScreen(conversation.id, conversation.title));
            }

            @Override
            public void onTogglePinned(Conversation conversation) {
                viewModel.togglePinned(conversation);
            }

            @Override
            public void onRename(Conversation conversation) {
                showRenameDialog(conversation);
            }

            @Override
            public void onDelete(Conversation conversation) {
                showDeleteDialog(conversation);
            }
        });
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        viewModel = new ViewModelProvider(this, QianwenViewModelJava.factory(getApplication()))
                .get(QianwenViewModelJava.class);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!updatingSearchInput) {
                    viewModel.updateSearchQuery(s == null ? "" : s.toString());
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        createButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                viewModel.createConversation();
            }
        });
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showSettingsDialog();
            }
        });
        statusButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showStatusDialog();
            }
        });

        viewModel.state.observe(this, this::render);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从聊天页返回时，把状态里的导航目标同步回列表，避免再次自动进入聊天页。
        QianwenUiState state = viewModel.state.getValue();
        if (state != null && state.screen.isChat()) {
            launchedChatId = null;
            viewModel.navigate(NativeScreen.CONVERSATIONS);
        }
    }

    private void render(QianwenUiState state) {
        if (state == null) {
            return;
        }

        adapter.setItems(filterConversations(state));
        progressBar.setVisibility(state.listStatus == ConversationListStatus.LOADING ? View.VISIBLE : View.GONE);

        setMessageText(errorText, state.error);
        setMessageText(noticeText, state.notice);

        if (state.lastCacheSavedAt == null) {
            cacheText.setVisibility(View.GONE);
        } else {
            cacheText.setVisibility(View.VISIBLE);
            cacheText.setText("本地缓存：" + state.cacheStatus.label + " · " + state.lastCacheSavedAt);
        }

        String modelMode = state.health == null || state.health.modelMode == null
                ? state.serviceStatus.label
                : state.health.modelMode;
        serviceText.setText("服务端 " + modelMode);

        String query = state.searchQuery == null ? "" : state.searchQuery;
        if (!query.equals(searchInput.getText().toString())) {
            updatingSearchInput = true;
            searchInput.setText(query);
            searchInput.setSelection(query.length());
            updatingSearchInput = false;
        }

        if (state.screen.isChat()) {
            if (!state.screen.conversationId.equals(launchedChatId)) {
                launchedChatId = state.screen.conversationId;
                Intent intent = new Intent(this, ChatActivity.class);
                intent.putExtra(ChatActivity.EXTRA_CONV_ID, launchedChatId);
                intent.putExtra(ChatActivity.EXTRA_TITLE, state.screen.title);
                startActivity(intent);
            }
        } else {
            launchedChatId = null;
        }
    }

    private List<Conversation> filterConversations(QianwenUiState state) {
        String query = state.searchQuery == null ? "" : state.searchQuery.trim();
        if (query.isEmpty()) {
            return state.conversations;
        }
        List<Conversation> visible = new ArrayList<>();
        for (Conversation conversation : state.conversations) {
            String title = conversation.title == null ? "" : conversation.title;
            if (title.toLowerCase().contains(query.toLowerCase())) {
                visible.add(conversation);
            }
        }
        return visible;
    }

    private void showRenameDialog(final Conversation conversation) {
        final EditText input = new EditText(this);
        input.setText(conversation.title == null ? "" : conversation.title);
        input.setSelection(input.getText().length());
        new AlertDialog.Builder(this)
                .setTitle("重命名会话")
                .setView(input)
                .setPositiveButton("保存", (dialog, which) -> viewModel.renameConversation(conversation.id, input.getText().toString()))
                .setNegativeButton("取消", null)
                .show();
    }

    private void showDeleteDialog(final Conversation conversation) {
        new AlertDialog.Builder(this)
                .setTitle("删除会话")
                .setMessage((conversation.title == null ? "该会话" : conversation.title) + " 及其消息将被删除。")
                .setPositiveButton("删除", (dialog, which) -> viewModel.deleteConversation(conversation.id))
                .setNegativeButton("取消", null)
                .show();
    }

    private void showSettingsDialog() {
        String message = "当前 API：" + QianwenConfig.getBaseUrl() + "\n"
                + "模拟器：10.0.2.2 会映射到开发电脑的 localhost。\n"
                + "真机：调用 QianwenConfig.setBaseUrl() 指向电脑局域网 IP。\n"
                + "本地缓存：SharedPreferences 保存最近会话、消息和选中会话。";
        new AlertDialog.Builder(this)
                .setTitle("调试设置")
                .setMessage(message)
                .setPositiveButton("好", null)
                .show();
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
}
