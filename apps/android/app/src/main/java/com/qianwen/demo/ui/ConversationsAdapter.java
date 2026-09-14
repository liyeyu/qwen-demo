package com.qianwen.demo.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.qianwen.demo.data.Conversation;
import java.util.ArrayList;
import java.util.List;

public class ConversationsAdapter extends RecyclerView.Adapter<ConversationsAdapter.VH> {
    public interface Listener {
        void onClick(Conversation conversation);

        void onTogglePinned(Conversation conversation);

        void onRename(Conversation conversation);

        void onDelete(Conversation conversation);
    }

    private final List<Conversation> items = new ArrayList<>();
    private final Listener listener;

    public ConversationsAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<Conversation> list) {
        items.clear();
        if (list != null) {
            items.addAll(list);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        View view = LayoutInflater.from(context).inflate(com.qianwen.demo.R.layout.item_conversation, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        final Conversation conversation = items.get(position);
        holder.title.setText(conversation.title == null ? "(无标题)" : conversation.title);
        holder.sub.setText((conversation.pinned ? "已置顶 · " : "")
                + "更新: " + (conversation.updatedAt == null ? "" : conversation.updatedAt));
        holder.pin.setText(conversation.pinned ? "取消置顶" : "置顶");
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                listener.onClick(conversation);
            }
        });
        holder.pin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                listener.onTogglePinned(conversation);
            }
        });
        holder.rename.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                listener.onRename(conversation);
            }
        });
        holder.delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                listener.onDelete(conversation);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView sub;
        final Button pin;
        final Button rename;
        final Button delete;

        VH(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(com.qianwen.demo.R.id.title);
            sub = itemView.findViewById(com.qianwen.demo.R.id.sub);
            pin = itemView.findViewById(com.qianwen.demo.R.id.pin);
            rename = itemView.findViewById(com.qianwen.demo.R.id.rename);
            delete = itemView.findViewById(com.qianwen.demo.R.id.delete);
        }
    }
}
