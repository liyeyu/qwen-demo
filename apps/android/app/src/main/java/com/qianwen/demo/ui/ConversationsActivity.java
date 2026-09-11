package com.qianwen.demo.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.qianwen.demo.data.QianwenRepositoryJava;
import java.util.List;

public class ConversationsActivity extends AppCompatActivity {
    private QianwenViewModelJava viewModel;
    private ConversationsAdapter adapter;
    private ProgressBar progressBar;
    private TextView errorText;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.qianwen.demo.R.layout.activity_conversations);

        RecyclerView recycler = findViewById(com.qianwen.demo.R.id.recycler);
        progressBar = findViewById(com.qianwen.demo.R.id.progress);
        errorText = findViewById(com.qianwen.demo.R.id.error_text);
        Button create = findViewById(com.qianwen.demo.R.id.create_button);

        adapter = new ConversationsAdapter(conversation -> {
            // select conversation in viewmodel
            viewModel.selectConversation(conversation.id, conversation.title);
            Toast.makeText(ConversationsActivity.this, "已选择会话: " + conversation.title, Toast.LENGTH_SHORT).show();
        });
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        // ViewModel factory to provide repository
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

        viewModel.state.observe(this, state -> {
            if (state == null) return;
            // update UI
            List<com.qianwen.demo.data.Conversation> convs = state.conversations;
            adapter.setItems(convs);

            switch (state.listStatus) {
                case LOADING:
                    progressBar.setVisibility(View.VISIBLE);
                    errorText.setVisibility(View.GONE);
                    break;
                case OFFLINE:
                    progressBar.setVisibility(View.GONE);
                    errorText.setVisibility(View.VISIBLE);
                    errorText.setText("离线：显示本地缓存");
                    break;
                case EMPTY:
                    progressBar.setVisibility(View.GONE);
                    errorText.setVisibility(View.VISIBLE);
                    errorText.setText("暂无会话，请新建会话。");
                    break;
                case READY:
                default:
                    progressBar.setVisibility(View.GONE);
                    errorText.setVisibility(View.GONE);
                    break;
            }

            if (state.error != null) {
                Toast.makeText(ConversationsActivity.this, state.error, Toast.LENGTH_SHORT).show();
            }
        });

        create.setOnClickListener(v -> viewModel.createConversation());
    }
}
