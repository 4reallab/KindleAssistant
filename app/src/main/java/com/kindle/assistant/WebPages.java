package com.kindle.assistant;

import java.util.List;

/** HTTP 服务提供的门户页与待办页。 */
final class WebPages {

    private WebPages() {
    }

    static String portal(String baseUrl) {
        String mcp = mcpUrl(baseUrl);
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
                + "<a class=\"card\" href=\"/ai.html\"><b>AI 助手操作指南</b>"
                + "<span>在电脑端 AI 助手里用对话管理 Kindle。端点 "
                + escapeHtml(mcp) + "，点开看配置步骤</span></a>"
                + "</div></body></html>";
    }

    /** 把门户地址转成 MCP 端点，例如 http://192.168.31.192:8088/ -> .../mcp */
    private static String mcpUrl(String baseUrl) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/mcp";
    }

    /** 电脑端 AI 助手接入引导页：添加 MCP、启用/信任、发指令示例。 */
    static String aiGuide(String baseUrl) {
        String mcp = mcpUrl(baseUrl);
        String addInstruction = "请把下面这个 MCP 服务器添加到你的 MCP 配置里，"
                + "用你自己支持的方式（配置文件、设置界面或命令行都可以）："
                + "名称 kindle-assistant，传输类型 Streamable HTTP，URL " + mcp
                + "，目前无需鉴权。不要覆盖已有的服务器。"
                + "添加完成后告诉我需要怎样启用或信任它。";

        StringBuilder h = new StringBuilder();
        h.append("<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
                .append("<title>AI 助手操作指南 · Kindle助手</title><style>")
                .append("*{box-sizing:border-box}body{margin:0;min-height:100vh;background:#eef1f4;")
                .append("color:#1a1a1a;font-family:system-ui,-apple-system,'Segoe UI',sans-serif;")
                .append("display:flex;align-items:flex-start;justify-content:center;padding:24px 14px}")
                .append(".wrap{width:100%;max-width:680px}h1{font-size:23px;margin:0 0 6px}")
                .append(".sub{color:#6b7280;font-size:14px;margin:0 0 18px;line-height:1.6}")
                .append(".card{background:#fff;border-radius:14px;padding:18px 20px;margin-bottom:16px;")
                .append("box-shadow:0 6px 22px rgba(0,0,0,.06)}")
                .append(".card h2{font-size:18px;margin:0 0 12px}")
                .append(".card h2 i{display:inline-block;width:26px;height:26px;line-height:26px;")
                .append("text-align:center;background:#111;color:#fff;border-radius:50%;font-style:normal;")
                .append("font-size:14px;margin-right:8px}")
                .append(".card p{font-size:14px;line-height:1.7;margin:8px 0;color:#374151}")
                .append(".box{position:relative;margin:10px 0}")
                .append("textarea{width:100%;min-height:76px;padding:12px 12px 40px;")
                .append("border:1px solid #b8bec6;border-radius:10px;font-size:14px;line-height:1.6;")
                .append("color:#111;background:#fafbfc;resize:vertical;word-break:break-all}")
                .append("button.copy{position:absolute;right:8px;bottom:8px;padding:7px 12px;")
                .append("border:1px solid #111;border-radius:8px;background:#111;color:#fff;")
                .append("font-size:13px;font-weight:600;cursor:pointer}")
                .append(".tag{display:inline-block;padding:2px 8px;border-radius:6px;background:#eef1f4;")
                .append("color:#374151;font-size:12px;margin-right:6px}")
                .append(".example{margin:8px 0 0;padding:10px 12px;border:1px solid #e5e7eb;")
                .append("border-radius:10px;background:#fff}")
                .append(".example:first-of-type{margin-top:0}")
                .append(".example span{display:block;font-size:14px;color:#111;line-height:1.5;")
                .append("word-break:break-all;margin-bottom:6px}")
                .append(".example button{padding:5px 10px;border:1px solid #111;border-radius:7px;")
                .append("background:#fff;color:#111;font-size:12px;cursor:pointer}")
                .append("code{background:#eef1f4;padding:1px 5px;border-radius:4px;font-size:13px}")
                .append(".warn{background:#fff8e6;border-left:4px solid #d99b00}")
                .append(".back{display:inline-block;margin-bottom:14px;color:#111;font-size:14px}")
                .append("</style></head><body><div class=\"wrap\">")
                .append("<a class=\"back\" href=\"/\">← 返回功能选择</a>")
                .append("<h1>用 AI 助手操作 Kindle助手</h1>")
                .append("<p class=\"sub\">在<b>电脑上的 AI 助手</b>里配置一次，之后直接用对话就能管理 Kindle 的"
                        + "待办、图片、计时和台历。手机浏览器打开本页也能看，但添加服务和发指令都在电脑端完成。"
                        + "服务地址会随设备自动变化，下面的地址已是本机实际地址。</p>");

        // 步骤 1
        h.append("<div class=\"card\"><h2><i>1</i>添加 MCP 服务器</h2>")
                .append("<p><span class=\"tag\">推荐 · WorkBuddy</span>"
                        + "复制下面整段指令，发给电脑端 WorkBuddy，它会自动写入配置（不覆盖已有服务器），"
                        + "并告诉你下一步怎么启用或信任。</p>")
                .append("<div class=\"box\"><textarea id=\"addCmd\" readonly>")
                .append(escapeHtml(addInstruction))
                .append("</textarea><button class=\"copy\" onclick=\"copyText('addCmd',this)\">复制指令</button></div>")
                .append("<p><span class=\"tag\">豆包用户</span>"
                        + "普通豆包 App 不支持 MCP，请用<b>豆包工作版</b>（桌面端）并<b>手动添加</b>："
                        + "在设置里找到 MCP 服务器管理，点添加，选择「Streamable HTTP」，"
                        + "名称填 <code>kindle-assistant</code>，URL 填 <code>")
                .append(escapeHtml(mcp))
                .append("</code>，保存即可。其他 AI 助手若不支持对话添加，也用同样的信息手动填写。</p>")
                .append("</div>");

        // 步骤 2
        h.append("<div class=\"card\"><h2><i>2</i>启用或信任 MCP 服务</h2>")
                .append("<p><b>WorkBuddy：</b>添加后按它的提示点「信任 / 启用」；若没自动提示，"
                        + "到连接器（自定义 MCP 服务器）管理里找到 <code>kindle-assistant</code>，"
                        + "开启并信任，然后重开一个对话。</p>")
                .append("<p><b>豆包工作版：</b>在 MCP 服务器列表里把 <code>kindle-assistant</code> 的开关打开；"
                        + "首次调用时如弹出授权提示，确认允许即可。</p>")
                .append("<p>如果 AI 说找不到工具，让它重新列一次工具，或重启助手再试。目前无需鉴权，"
                        + "仅限这台 Kindle 所在的局域网使用。</p>")
                .append("</div>");

        // 步骤 3
        h.append("<div class=\"card\"><h2><i>3</i>在 AI 助手里发指令（举例）</h2>")
                .append("<p>下面是一些可以直接发的指令，点「复制」发给电脑端 AI 助手即可。</p>");
        String[] examples = {
                "列出我 Kindle 上所有未完成的待办",
                "添加一条待办：明天上午十点交材料",
                "把「单词书写」标记为已完成",
                "给「阅读打卡」累加 25 分钟耗时",
                "列出 Kindle 上已接收的图片",
                "开始一个 25 分钟的计时，关联到「计算小能手」",
                "把电子台历的城市改成上海市",
                "看一下 Kindle 的服务状态，现在有几个待办"
        };
        for (int i = 0; i < examples.length; i++) {
            h.append("<div class=\"example\"><span>").append(escapeHtml(examples[i]))
                    .append("</span><button onclick=\"copyExample(this)\">复制</button></div>");
        }
        h.append("</div>");

        h.append("<div class=\"card warn\"><p><b>安全提示：</b>该接口能读写设备上的待办、图片、计时和台历，"
                + "无鉴权、仅局域网可用。不要把 8088 端口暴露到公网；需要口令时可用 "
                + "<code>mcp_set_token</code> 工具开启 Bearer 认证。</p></div>");

        h.append("<script>")
                .append("function doCopy(text,btn){var ok=false;")
                .append("try{if(navigator.clipboard&&window.isSecureContext){navigator.clipboard.writeText(text);ok=true;}}catch(e){}")
                .append("if(!ok){var t=document.createElement('textarea');t.value=text;")
                .append("t.style.position='fixed';t.style.left='-9999px';document.body.appendChild(t);")
                .append("t.select();try{ok=document.execCommand('copy');}catch(e){}document.body.removeChild(t);}")
                .append("var old=btn.textContent;btn.textContent=ok?'已复制 ✓':'请手动复制';")
                .append("setTimeout(function(){btn.textContent=old;},1800);}")
                .append("function copyText(id,btn){doCopy(document.getElementById(id).value,btn);}")
                .append("function copyExample(btn){doCopy(btn.parentNode.querySelector('span').textContent,btn);}")
                .append("</script></div></body></html>");
        return h.toString();
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
