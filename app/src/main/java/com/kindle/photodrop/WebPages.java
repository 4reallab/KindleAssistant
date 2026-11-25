package com.kindle.photodrop;

import java.util.List;

/** HTTP 服务提供的门户页与待办页。 */
final class WebPages {

    private WebPages() {
    }

    static String portal() {
        return "<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>Kindle助手</title><style>"
                + "*{box-sizing:border-box}body{margin:0;min-height:100vh;background:#eef1f4;"
                + "color:#1a1a1a;font-family:system-ui,-apple-system,'Segoe UI',sans-serif;"
                + "display:flex;align-items:flex-start;justify-content:center;padding:28px 16px}"
                + ".wrap{width:100%;max-width:520px}h1{font-size:24px;margin:0 0 6px}"
                + ".sub{color:#6b7280;font-size:14px;margin:0 0 22px}"
                + "a.card{display:block;background:#fff;border:2px solid #111;border-radius:16px;"
                + "padding:28px;margin-bottom:16px;text-decoration:none;color:#111;"
                + "box-shadow:0 8px 30px rgba(0,0,0,.06)}"
                + "a.card b{display:block;font-size:21px}a.card span{display:block;margin-top:8px;"
                + "font-size:14px;color:#6b7280}"
                + "</style></head><body><div class=\"wrap\"><h1>Kindle助手</h1>"
                + "<p class=\"sub\">选择要使用的功能</p>"
                + "<a class=\"card\" href=\"/upload.html\"><b>上传图片</b>"
                + "<span>手机选图后发送到 Kindle，接收后自动全屏打开</span></a>"
                + "<a class=\"card\" href=\"/todo\"><b>待办事项</b>"
                + "<span>添加、修改、删除待办，标记完成或恢复</span></a>"
                + "</div></body></html>";
    }

    static String todo(String query) {
        int active = 0;
        if (query != null) {
            for (String pair : query.split("&")) {
                if (pair.startsWith("tab=")) {
                    try {
                        active = Integer.parseInt(pair.substring(4));
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        if (active < 0 || active > 2) {
            active = 0;
        }
        final int selected = active;

        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
                .append("<title>待办事项 · Kindle助手</title><style>")
                .append("*{box-sizing:border-box}body{margin:0;min-height:100vh;background:#eef1f4;")
                .append("color:#1a1a1a;font-family:system-ui,-apple-system,'Segoe UI',sans-serif;")
                .append("display:flex;align-items:flex-start;justify-content:center;padding:24px 14px}")
                .append(".wrap{width:100%;max-width:680px}h1{font-size:23px;margin:0 0 16px}")
                .append(".card{background:#fff;border-radius:14px;padding:18px;margin-bottom:16px;")
                .append("box-shadow:0 6px 22px rgba(0,0,0,.06)}")
                .append(".add{display:flex;gap:8px;margin-bottom:18px}")
                .append("input[type=text]{flex:1;min-width:0;padding:12px;border:1px solid #b8bec6;")
                .append("border-radius:8px;font-size:16px}")
                .append("button{padding:9px 12px;border:1px solid #111;border-radius:8px;background:#fff;")
                .append("color:#111;font-size:14px;font-weight:600;cursor:pointer;white-space:nowrap}")
                .append("button.primary{background:#111;color:#fff}")
                .append("button.danger{border-color:#b3261e;color:#b3261e}")
                .append(".row{padding:10px 0;border-bottom:1px solid #e5e7eb}")
                .append(".row-top{display:flex;align-items:center;gap:8px;flex-wrap:wrap}")
                .append(".row .title{flex:1;min-width:140px;font-size:16px;word-break:break-all}")
                .append(".row .time{display:block;margin-top:6px;font-size:12px;color:#6b7280}")
                .append(".row.done .title{text-decoration:line-through;color:#6b7280}")
                .append(".row .top-form{display:inline-flex}")
                .append(".row .more{min-width:38px;padding:8px 10px;font-size:18px;line-height:1}")
                .append(".row-menu{display:none;margin-top:10px;padding:10px;border:1px solid #d1d5db;")
                .append("border-radius:10px;background:#f9fafb;gap:8px;align-items:center;flex-wrap:wrap}")
                .append(".row-menu.show{display:flex}")
                .append(".row-menu form{display:inline-flex;gap:6px}")
                .append(".row-menu input[type=text]{flex:1;min-width:160px;padding:8px;font-size:14px}")
                .append(".row-menu button{padding:8px 10px;font-size:13px}")
                .append(".empty{color:#6b7280;font-size:14px;padding:6px 0}")
                .append(".back{display:inline-block;margin-bottom:14px;color:#111}")
                .append(".tabs{display:flex;gap:8px;margin-bottom:16px}")
                .append(".tabs button{flex:1;padding:12px 4px;font-size:15px}")
                .append(".tabs button.active{background:#111;color:#fff}")
                .append(".section{display:none}.section.show{display:block}")
                .append("</style></head><body><div class=\"wrap\">")
                .append("<a class=\"back\" href=\"/\">← 返回功能选择</a><h1>待办事项</h1>")
                .append("<div class=\"tabs\">")
                .append(tabButton(0, "未完成", selected))
                .append(tabButton(1, "已完成", selected))
                .append(tabButton(2, "已删除", selected))
                .append("</div>")
                .append("<form class=\"add\" method=\"post\" action=\"/todo/add\">")
                .append(hiddenTab(selected))
                .append("<input type=\"text\" name=\"title\" placeholder=\"输入新的待办事项\" required>")
                .append("<button class=\"primary\" type=\"submit\">添加</button></form>");

        appendSection(html, TodoStore.PENDING, "未完成", selected);
        appendSection(html, TodoStore.DONE, "已完成", selected);
        appendSection(html, TodoStore.DELETED, "已删除", selected);

        html.append("<script>function showTab(i,btn){"
                + "var s=document.querySelectorAll('.section'),b=document.querySelectorAll('.tabs button');"
                + "for(var j=0;j<s.length;j++)s[j].className='section'+(j===i?' show':'');"
                + "for(var j=0;j<b.length;j++)b[j].className=j===i?'active':'';"
                + "}function toggleMenu(btn){"
                + "var menu=btn.parentNode.parentNode.querySelector('.row-menu');"
                + "if(menu){var open=menu.classList.toggle('show');"
                + "btn.setAttribute('aria-expanded',open?'true':'false');}"
                + "}</script></div></body></html>");
        return html.toString();
    }

    private static void appendSection(StringBuilder html, int status, String name, int selected) {
        List<TodoStore.Todo> items = TodoStore.list(status);
        html.append("<div class=\"section")
                .append(status == selected ? " show" : "")
                .append("\"><div class=\"card\">");
        if (items.isEmpty()) {
            html.append("<div class=\"empty\">暂无事项</div>");
        }
        for (TodoStore.Todo todo : items) {
            html.append("<div class=\"row");
            if (status == TodoStore.DONE) {
                html.append(" done");
            }
            html.append("\"><div class=\"row-top\"><span class=\"title\">")
                    .append(escapeHtml(todo.title))
                    .append("</span>");

            if (status != TodoStore.PENDING) {
                html.append("<form class=\"top-form\" method=\"post\" action=\"/todo/status\">")
                        .append(hidden(todo.id)).append(hiddenStatus(TodoStore.PENDING))
                        .append(hiddenTab(selected))
                        .append("<button type=\"submit\">恢复</button></form>");
            } else {
                html.append("<form class=\"top-form\" method=\"post\" action=\"/todo/status\">")
                        .append(hidden(todo.id)).append(hiddenStatus(TodoStore.DONE))
                        .append(hiddenTab(selected))
                        .append("<button type=\"submit\">完成</button></form>");
            }

            html.append("<button class=\"more\" type=\"button\" title=\"更多操作\" ")
                    .append("aria-label=\"更多操作\" aria-expanded=\"false\" ")
                    .append("onclick=\"toggleMenu(this)\">⋯</button>")
                    .append("</div><span class=\"time\">")
                    .append(TodoStore.timeText(todo))
                    .append("</span><div class=\"row-menu\">");

            html.append("<form method=\"post\" action=\"/todo/update\">")
                    .append(hidden(todo.id)).append(hiddenTab(selected))
                    .append("<input type=\"text\" name=\"title\" value=\"")
                    .append(escapeHtml(todo.title)).append("\" required>")
                    .append("<button type=\"submit\">保存修改</button></form>");

            html.append("<form method=\"post\" action=\"/todo/delete\" ")
                    .append("onsubmit=\"return confirm('确定删除这条待办？')\">")
                    .append(hidden(todo.id)).append(hiddenTab(selected));
            if (status == TodoStore.DELETED) {
                html.append("<input type=\"hidden\" name=\"permanent\" value=\"1\">");
            }
            html.append("<button class=\"danger\" type=\"submit\">")
                    .append(status == TodoStore.DELETED ? "彻底删除" : "删除")
                    .append("</button></form></div></div>");
        }
        html.append("</div></div>");
    }

    private static String tabButton(int status, String name, int selected) {
        int count = TodoStore.list(status).size();
        return "<button type=\"button\" class=\"" + (status == selected ? "active" : "")
                + "\" onclick=\"showTab(" + status + ",this)\">" + name
                + " (" + count + ")</button>";
    }

    private static String hiddenTab(int status) {
        return "<input type=\"hidden\" name=\"tab\" value=\"" + status + "\">";
    }

    private static String hidden(long id) {
        return "<input type=\"hidden\" name=\"id\" value=\"" + id + "\">";
    }

    private static String hiddenStatus(int status) {
        return "<input type=\"hidden\" name=\"status\" value=\"" + status + "\">";
    }

    static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
