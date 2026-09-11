package com.qianwen.demo.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.qianwen.demo.data.ChatMessage;
import com.qianwen.demo.data.QianwenRepositoryJava;
import java.util.List;

public class ChatActivity extends AppCompatActivity {
    public static final String EXTRA_CONV_ID = "conv_id";
    public static final String EXTRA_TITLE = "title";

    private QianwenViewModelJava viewModel;
    private MessagesAdapter adapter;
    private RecyclerView recycler;
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
        setContentView(com.qianwen.demo.R.layout.activity_chat);

        recycler = findViewById(com.qianwen.demo.R.id.messages_recycler);
        EditText input = findViewById(com.qianwen.demo.R.id.input);
        Button send = findViewById(com.qianwen.demo.R.id.send_button);
        Button cancel = findViewById(com.qianwen.demo.R.id.cancel_button);

        adapter = new MessagesAdapter();
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        String convId = getIntent().getStringExtra(EXTRA_CONV_ID);
        String title = getIntent().getStringExtra(EXTRA_TITLE);

        ViewModelProvider.Factory factory = new ViewModelProvider.Factory() {
            @SuppressWarnings("unchecked")
            @Override
            public <T extends androidx.lifecycle.ViewModel> T create(Class<T> modelClass) {
                if (modelClass.isAssignableFrom(QianwenViewModelJava.class)) {
                    QianwenRepositoryJava repo = new QianwenRepositoryJava(getApplicationContext());
                    return (T) new QianwenViewModelJava(repo);
                }
                throw new IllegalArgumentException("Unknown ViewModel class");
            }
        };

        viewModel = new ViewModelProvider(this, factory).get(QianwenViewModelJava.class);

        if (convId != null) {
            viewModel.selectConversation(convId, title == null ? "" : title);
        }

        viewModel.state.observe(this, state -> {
            if (state == null) return;
            List<ChatMessage> msgs = state.messagesByConversation.get(state.selectedConversationId);
            adapter.setItems(msgs);

            // debounce scroll to bottom to avoid excessive UI work on fast streaming updates
            uiHandler.removeCallbacks(scrollRunnable);
            uiHandler.postDelayed(scrollRunnable, 120);

            // update draft if different
            String draft = state.draft == null ? "" : state.draft;
            if (!input.getText().toString().equals(draft)) {
                // preserve cursor position: set only when different
                input.setText(draft);
                input.setSelection(draft.length());
            }

            // update send/cancel button states based on streaming
            boolean streaming = state.isStreaming();
            send.setEnabled(!streaming);
            cancel.setEnabled(streaming);

            // optionally change send button text when streaming (kept simple: disabled)
        });

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                viewModel.updateDraft(s == null ? "" : s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        send.setOnClickListener(v -> viewModel.sendMessage());
        cancel.setOnClickListener(v -> viewModel.cancelSending());
    }

    private void scrollToBottomIfNeeded() {
        if (recycler == null || adapter == null) return;
        int count = adapter.getItemCount();
        if (count <= 0) return;
        // scroll to last item
        recycler.scrollToPosition(count - 1);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        uiHandler.removeCallbacksAndMessages(null);
    }
}
