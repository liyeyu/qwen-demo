package com.qianwen.demo.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.qianwen.demo.data.Conversation;
import java.util.ArrayList;
import java.util.List;

public class ConversationsAdapter extends RecyclerView.Adapter<ConversationsAdapter.VH> {
    public interface OnItemClickListener {
        void onClick(Conversation conversation);
    }

    private final List<Conversation> items = new ArrayList<>();
    private final OnItemClickListener listener;

    public ConversationsAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<Conversation> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context ctx = parent.getContext();
        View v = LayoutInflater.from(ctx).inflate(com.qianwen.demo.R.layout.item_conversation, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Conversation c = items.get(position);
        holder.title.setText(c.title == null ? "(无标题)" : c.title);
        holder.sub.setText("更新: " + (c.updatedAt == null ? "" : c.updatedAt));
        holder.itemView.setOnClickListener(v -> listener.onClick(c));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView sub;

        VH(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(com.qianwen.demo.R.id.title);
            sub = itemView.findViewById(com.qianwen.demo.R.id.sub);
        }
    }
}
