package com.kindle.photodrop;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** 待办事项：未完成/已完成/已删除三类，支持增删改与进入计时。 */
public class TodoActivity extends Activity {

    private int currentStatus = TodoStore.PENDING;
    private List<TodoStore.Todo> items = new ArrayList<>();
    private TodoAdapter adapter;
    private TextView emptyText;
    private Button tabPending;
    private Button tabDone;
    private Button tabDeleted;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            reload();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_todo);
        TodoStore.load(this);

        tabPending = findViewById(R.id.tabPending);
        tabDone = findViewById(R.id.tabDone);
        tabDeleted = findViewById(R.id.tabDeleted);
        tabPending.setOnClickListener(v -> switchTab(TodoStore.PENDING));
        tabDone.setOnClickListener(v -> switchTab(TodoStore.DONE));
        tabDeleted.setOnClickListener(v -> switchTab(TodoStore.DELETED));

        ListView list = findViewById(R.id.todoList);
        adapter = new TodoAdapter();
        list.setAdapter(adapter);

        emptyText = findViewById(R.id.emptyText);
        findViewById(R.id.addButton).setOnClickListener(v -> showAddDialog());

        switchTab(TodoStore.PENDING);
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(receiver, new IntentFilter(TodoStore.ACTION_CHANGED));
        reload();
    }

    @Override
    protected void onStop() {
        unregisterReceiver(receiver);
        super.onStop();
    }

    private void openTimer(TodoStore.Todo todo) {
        Intent intent = new Intent(this, TimerActivity.class);
        intent.putExtra("id", todo.id);
        intent.putExtra("title", todo.title);
        startActivity(intent);
    }

    private void switchTab(int status) {
        currentStatus = status;
        reload();
    }

    private void reload() {
        items = TodoStore.list(currentStatus);
        emptyText.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        tabPending.setText(getString(R.string.tab_count, "未完成",
                TodoStore.list(TodoStore.PENDING).size()));
        tabDone.setText(getString(R.string.tab_count, "已完成",
                TodoStore.list(TodoStore.DONE).size()));
        tabDeleted.setText(getString(R.string.tab_count, "已删除",
                TodoStore.list(TodoStore.DELETED).size()));
        styleTab(tabPending, TodoStore.PENDING);
        styleTab(tabDone, TodoStore.DONE);
        styleTab(tabDeleted, TodoStore.DELETED);
        adapter.notifyDataSetChanged();
    }

    private void styleTab(Button tab, int status) {
        boolean active = status == currentStatus;
        tab.setBackgroundColor(active ? 0xFF000000 : 0xFFFFFFFF);
        tab.setTextColor(active ? 0xFFFFFFFF : 0xFF000000);
    }

    private void showAddDialog() {
        EditText input = new EditText(this);
        input.setHint("输入待办内容");
        input.setTextSize(18);
        new AlertDialog.Builder(this)
                .setTitle("添加待办")
                .setView(input)
                .setPositiveButton(R.string.add, (dialog, which) -> {
                    String value = input.getText().toString();
                    if (value.trim().length() == 0) {
                        Toast.makeText(this, "内容不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    TodoStore.add(this, value);
                    reload();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showEditDialog(TodoStore.Todo todo) {
        EditText input = new EditText(this);
        input.setText(todo.title);
        input.setSelection(input.getText().length());
        input.setTextSize(18);
        new AlertDialog.Builder(this)
                .setTitle("修改待办")
                .setView(input)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String value = input.getText().toString();
                    if (value.trim().length() == 0) {
                        Toast.makeText(this, "内容不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    TodoStore.update(this, todo.id, value);
                    reload();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showItemMenu(View anchor, TodoStore.Todo todo) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, R.string.edit);
        popup.getMenu().add(0, 2, 1,
                todo.status == TodoStore.DELETED ? R.string.todo_delete_permanent : R.string.delete);
        popup.setOnMenuItemClickListener((MenuItem item) -> {
            if (item.getItemId() == 1) {
                showEditDialog(todo);
            } else if (item.getItemId() == 2) {
                confirmDelete(todo);
            }
            return true;
        });
        popup.show();
    }

    private void confirmDelete(TodoStore.Todo todo) {
        boolean permanent = todo.status == TodoStore.DELETED;
        String message = permanent
                ? "彻底删除「" + todo.title + "」？此操作不可恢复。"
                : "删除「" + todo.title + "」？删除后可在「已删除」中恢复。";
        new AlertDialog.Builder(this)
                .setTitle("确认删除")
                .setMessage(message)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    if (permanent) {
                        TodoStore.remove(this, todo.id);
                    } else {
                        TodoStore.setStatus(this, todo.id, TodoStore.DELETED);
                    }
                    reload();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private final class TodoAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return items.get(position).id;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = getLayoutInflater().inflate(R.layout.item_todo, parent, false);
            }
            TodoStore.Todo todo = items.get(position);
            view.setOnClickListener(v -> openTimer(todo));
            TextView name = view.findViewById(R.id.todoTitle);
            TextView time = view.findViewById(R.id.todoTime);
            Button action = view.findViewById(R.id.todoAction);
            Button more = view.findViewById(R.id.todoMore);
            name.setText(todo.title);
            time.setText(TodoStore.timeText(todo));
            if (todo.status == TodoStore.PENDING) {
                action.setText(R.string.todo_finish);
                action.setOnClickListener(v ->
                        TodoStore.setStatus(TodoActivity.this, todo.id, TodoStore.DONE));
            } else {
                action.setText(R.string.todo_restore);
                action.setOnClickListener(v ->
                        TodoStore.setStatus(TodoActivity.this, todo.id, TodoStore.PENDING));
            }
            more.setOnClickListener(v -> showItemMenu(v, todo));
            return view;
        }
    }
}
