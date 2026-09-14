package com.qianwen.demo.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import com.qianwen.demo.data.ChatMessage;
import com.qianwen.demo.data.NewsItem;
import java.util.ArrayList;
import java.util.List;

public class MessagesAdapter extends RecyclerView.Adapter<MessagesAdapter.VH> {
    /** 新闻缩略图边长（dp），与 item_news.xml 中 news_image 的尺寸保持一致。 */
    private static final int NEWS_IMAGE_SIZE_DP = 56;

    public interface Listener {
        void onEdit(ChatMessage message);

        void onRegenerate(ChatMessage message);

        void onNewsClick(NewsItem item);
    }

    private final List<ChatMessage> items = new ArrayList<>();
    private final Listener listener;

    public MessagesAdapter(Listener listener) {
        this.listener = listener;
    }

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
                boolean sameType = (o.type == null ? "" : o.type).equals(n.type == null ? "" : n.type);
                return sameContent && sameRole && sameStatus && sameError && sameType && sameNews(o.news, n.news);
            }

            @Override
            public Object getChangePayload(int oldItemPosition, int newItemPosition) {
                // Return a list of changed fields so onBindViewHolder can do partial updates
                ChatMessage o = old.get(oldItemPosition);
                ChatMessage n = items.get(newItemPosition);
                if (o == null || n == null) return null;
                // 新闻卡片涉及图片与列表增删，直接整体重绑，避免局部刷新漏更新
                if (o.hasNews() || n.hasNews()) return null;
                List<String> changes = new ArrayList<>();
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

        boolean newsMessage = m.hasNews();
        if (newsMessage) {
            // 新闻列表消息：正文位让给卡片列表，并清掉上一轮复用残留
            holder.content.setVisibility(View.GONE);
            holder.content.setText("");
            holder.newsContainer.setVisibility(View.VISIBLE);
            bindNews(holder, m.news);
        } else {
            holder.content.setVisibility(View.VISIBLE);
            holder.content.setText(m.content == null ? "" : m.content);
            holder.newsContainer.setVisibility(View.GONE);
            holder.newsContainer.removeAllViews();
        }

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

        boolean showActions = !newsMessage && !m.isUser();
        holder.actions.setVisibility(showActions ? View.VISIBLE : View.GONE);
        if (showActions) {
            holder.edit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    listener.onEdit(m);
                }
            });
            holder.regenerate.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    listener.onRegenerate(m);
                }
            });
        }
    }

    /** 按 news 列表填充卡片：每条为「左正方形小图 + 右最多两行标题」。 */
    private void bindNews(VH holder, List<NewsItem> news) {
        holder.newsContainer.removeAllViews();
        if (news == null) {
            return;
        }
        Context context = holder.itemView.getContext();
        LayoutInflater inflater = LayoutInflater.from(context);
        int targetSizePx = Math.round(NEWS_IMAGE_SIZE_DP * context.getResources().getDisplayMetrics().density);

        for (NewsItem item : news) {
            if (item == null) {
                continue;
            }
            View row = inflater.inflate(com.qianwen.demo.R.layout.item_news, holder.newsContainer, false);
            TextView title = row.findViewById(com.qianwen.demo.R.id.news_title);
            ImageView image = row.findViewById(com.qianwen.demo.R.id.news_image);
            title.setText(item.title == null ? "" : item.title);
            ImageLoader.get().load(item.img, image, targetSizePx);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    listener.onNewsClick(item);
                }
            });
            holder.newsContainer.addView(row);
        }
    }

    private static boolean sameNews(List<NewsItem> left, List<NewsItem> right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null || left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            NewsItem o = left.get(i);
            NewsItem n = right.get(i);
            if (o == null || n == null) {
                if (o != n) {
                    return false;
                }
                continue;
            }
            if (!equalsSafe(o.id, n.id) || !equalsSafe(o.title, n.title) || !equalsSafe(o.img, n.img)) {
                return false;
            }
        }
        return true;
    }

    private static boolean equalsSafe(String left, String right) {
        return left == null ? right == null : left.equals(right);
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
                            onBindViewHolder(holder, position);
                            return;
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
        final LinearLayout newsContainer;
        final LinearLayout actions;
        final Button edit;
        final Button regenerate;

        VH(@NonNull View itemView) {
            super(itemView);
            role = itemView.findViewById(com.qianwen.demo.R.id.role);
            content = itemView.findViewById(com.qianwen.demo.R.id.content);
            status = itemView.findViewById(com.qianwen.demo.R.id.status);
            error = itemView.findViewById(com.qianwen.demo.R.id.error);
            newsContainer = itemView.findViewById(com.qianwen.demo.R.id.news_container);
            actions = itemView.findViewById(com.qianwen.demo.R.id.actions);
            edit = itemView.findViewById(com.qianwen.demo.R.id.edit_button);
            regenerate = itemView.findViewById(com.qianwen.demo.R.id.regenerate_button);
        }
    }
}
