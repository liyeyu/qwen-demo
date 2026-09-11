package com.qianwen.demo.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import com.qianwen.demo.data.ChatMessage;
import java.util.ArrayList;
import java.util.List;

public class MessagesAdapter extends RecyclerView.Adapter<MessagesAdapter.VH> {
    private final List<ChatMessage> items = new ArrayList<>();

    public void setItems(List<ChatMessage> list) {
        final List<ChatMessage> old = new ArrayList<>(items);
        items.clear();
        if (list != null) items.addAll(list);

        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return old.size();
            }

            @Override
            public int getNewListSize() {
                return items.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                ChatMessage o = old.get(oldItemPosition);
                ChatMessage n = items.get(newItemPosition);
                if (o.id == null || n.id == null) {
                    return o == n;
                }
                return o.id.equals(n.id);
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                ChatMessage o = old.get(oldItemPosition);
                ChatMessage n = items.get(newItemPosition);
                if (o == n) return true;
                if (o == null || n == null) return false;
                // compare relevant fields
                boolean sameContent = (o.content == null ? "" : o.content).equals(n.content == null ? "" : n.content);
                boolean sameRole = (o.role == null ? "" : o.role).equals(n.role == null ? "" : n.role);
                boolean sameStatus = (o.status == null ? "" : o.status).equals(n.status == null ? "" : n.status);
                boolean sameError = (o.error == null ? "" : o.error).equals(n.error == null ? "" : n.error);
                return sameContent && sameRole && sameStatus && sameError;
            }

            @Override
            public Object getChangePayload(int oldItemPosition, int newItemPosition) {
                // Use full bind for simplicity; could return specific payloads
                return null;
            }
        });

        diffResult.dispatchUpdatesTo(this);
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
