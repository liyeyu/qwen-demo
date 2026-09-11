package com.qianwen.demo.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.qianwen.demo.data.ChatMessage;
import java.util.ArrayList;
import java.util.List;

public class MessagesAdapter extends RecyclerView.Adapter<MessagesAdapter.VH> {
    private final List<ChatMessage> items = new ArrayList<>();

    public void setItems(List<ChatMessage> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context ctx = parent.getContext();
        View v = LayoutInflater.from(ctx).inflate(com.qianwen.demo.R.layout.item_message, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        ChatMessage m = items.get(position);
        holder.role.setText(m.role == null ? "" : m.role);
        holder.content.setText(m.content == null ? "" : m.content);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView role;
        final TextView content;

        VH(@NonNull View itemView) {
            super(itemView);
            role = itemView.findViewById(com.qianwen.demo.R.id.role);
            content = itemView.findViewById(com.qianwen.demo.R.id.content);
        }
    }
}
