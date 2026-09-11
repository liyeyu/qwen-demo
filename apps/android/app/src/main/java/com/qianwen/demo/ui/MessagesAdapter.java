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
                // Return a list of changed fields so onBindViewHolder can do partial updates
                ChatMessage o = old.get(oldItemPosition);
                ChatMessage n = items.get(newItemPosition);
                List<String> changes = new ArrayList<>();
                if (o == null || n == null) return changes;
                if (!(o.content == null ? "" : o.content).equals(n.content == null ? "" : n.content)) {
                    changes.add("content");
                }
                if (!(o.role == null ? "" : o.role).equals(n.role == null ? "" : n.role)) {
                    changes.add("role");
                }
                if (!(o.status == null ? "" : o.status).equals(n.status == null ? "" : n.status)) {
                    changes.add("status");
                }
                if (!(o.error == null ? "" : o.error).equals(n.error == null ? "" : n.error)) {
                    changes.add("error");
                }
                return changes;
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
        if (m.status == null || m.status.isEmpty()) {
            holder.status.setVisibility(View.GONE);
        } else {
            holder.status.setVisibility(View.VISIBLE);
            holder.status.setText(m.status);
        }
        if (m.error == null || m.error.isEmpty()) {
            holder.error.setVisibility(View.GONE);
        } else {
            holder.error.setVisibility(View.VISIBLE);
            holder.error.setText(m.error);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position, @NonNull List<Object> payloads) {
        if (payloads != null && !payloads.isEmpty()) {
            Object payload = payloads.get(0);
            if (payload instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> changes = (List<String>) payload;
                ChatMessage m = items.get(position);
                for (String key : changes) {
                    switch (key) {
                        case "content":
                            holder.content.setText(m.content == null ? "" : m.content);
                            break;
                        case "role":
                            holder.role.setText(m.role == null ? "" : m.role);
                            break;
                        case "status":
                            if (m.status == null || m.status.isEmpty()) {
                                holder.status.setVisibility(View.GONE);
                            } else {
                                holder.status.setVisibility(View.VISIBLE);
                                holder.status.setText(m.status);
                            }
                            break;
                        case "error":
                            if (m.error == null || m.error.isEmpty()) {
                                holder.error.setVisibility(View.GONE);
                            } else {
                                holder.error.setVisibility(View.VISIBLE);
                                holder.error.setText(m.error);
                            }
                            break;
                        default:
                            // fallback to full bind
                            holder.role.setText(m.role == null ? "" : m.role);
                            holder.content.setText(m.content == null ? "" : m.content);
                            if (m.status == null || m.status.isEmpty()) {
                                holder.status.setVisibility(View.GONE);
                            } else {
                                holder.status.setVisibility(View.VISIBLE);
                                holder.status.setText(m.status);
                            }
                            if (m.error == null || m.error.isEmpty()) {
                                holder.error.setVisibility(View.GONE);
                            } else {
                                holder.error.setVisibility(View.VISIBLE);
                                holder.error.setText(m.error);
                            }
                            break;
                    }
                }
                return;
            }
        }
        // default full bind
        onBindViewHolder(holder, position);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView role;
        final TextView content;
        final TextView status;
        final TextView error;

        VH(@NonNull View itemView) {
            super(itemView);
            role = itemView.findViewById(com.qianwen.demo.R.id.role);
            content = itemView.findViewById(com.qianwen.demo.R.id.content);
            status = itemView.findViewById(com.qianwen.demo.R.id.status);
            error = itemView.findViewById(com.qianwen.demo.R.id.error);
        }
    }
}
