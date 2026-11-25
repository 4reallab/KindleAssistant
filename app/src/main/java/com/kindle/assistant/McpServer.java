package com.kindle.assistant;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * 给 AI 助手用的 MCP（Model Context Protocol）服务端。
 * 传输采用 Streamable HTTP：客户端 POST /mcp，服务端返回单个 JSON-RPC 2.0 响应。
 * 所有读写都直接复用 Kindle 助手自己的 Store / Activity 状态，不引入额外数据库。
 */
final class McpServer {

    static final String ENDPOINT = "/mcp";
    static final String PROTOCOL = "2025-06-18";
    static final String SUPPORTED_PROTOCOL_2 = "2025-11-25";
    static final String SERVER_VERSION = "1.0.0";
    static final int MAX_BODY = 16 * 1024 * 1024;

    private static final String PREFS = "mcp_settings";
    private static final String KEY_TOKEN = "token";

    private McpServer() {
    }

    /** 未设置 token 时局域网内可直接访问；设置后所有 /mcp 请求都要带 Bearer token。 */
    static boolean authorized(Context context, Map<String, String> headers) {
        String token = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_TOKEN, "");
        if (token == null || token.isEmpty()) {
            return true;
        }
        String auth = headers == null ? null : headers.get("authorization");
        return auth != null && auth.equals("Bearer " + token);
    }

    /** 通知类消息返回 null，表示 HTTP 202 空响应。 */
    static String handle(Context context, String body) {
        JSONObject request;
        try {
            request = new JSONObject(body);
        } catch (Exception e) {
            return error(JSONObject.NULL, -32700, "JSON parse error");
        }

        Object id = request.opt("id");
        boolean notification = !request.has("id") || id == JSONObject.NULL;
        String method = request.optString("method", "");
        JSONObject params = request.optJSONObject("params");
        if (params == null) {
            params = new JSONObject();
        }

        try {
            Object result = dispatch(context, method, params);
            if (notification) {
                return null;
            }
            return obj("jsonrpc", "2.0", "id", id, "result", result).toString();
        } catch (RpcException e) {
            return error(id, e.code, e.getMessage());
        } catch (Exception e) {
            return error(id, -32603, "Internal error: " + safeMessage(e));
        }
    }

    private static Object dispatch(Context context, String method, JSONObject params)
            throws RpcException {
        switch (method) {
            case "initialize":
                return initialize(params);
            case "ping":
                return obj("resultType", "complete");
            case "tools/list":
                return obj("tools", toolsList(), "resultType", "complete");
            case "tools/call":
                return callTool(context, params);
            case "resources/list":
                return obj("resources", resourcesList(), "resultType", "complete");
            case "resources/read":
                return obj("contents", resourcesRead(context, params), "resultType", "complete");
            case "prompts/list":
                return obj("prompts", new JSONArray(), "resultType", "complete");
            case "notifications/initialized":
                return obj();
            case "logging/setLevel":
                return obj("resultType", "complete");
            default:
                throw new RpcException(-32601, "Method not found: " + method);
        }
    }

    private static JSONObject initialize(JSONObject params) {
        String requested = params.optString("protocolVersion", PROTOCOL);
        String version = (PROTOCOL.equals(requested) || SUPPORTED_PROTOCOL_2.equals(requested))
                ? requested : PROTOCOL;
        return obj(
                "protocolVersion", version,
                "capabilities", obj(
                        "resources", obj("subscribe", false, "listChanged", false),
                        "tools", obj("listChanged", false),
                        "logging", obj()),
                "serverInfo", obj("name", "kindle-assistant", "version", SERVER_VERSION),
                "instructions", "Kindle 助手的 MCP 服务。用 resources 读取全部数据，用 tools 修改待办、"
                        + "图片、计时器和台历设置。所有操作仅在 Kindle 局域网内生效。",
                "resultType", "complete");
    }

    private static JSONArray toolsList() {
        JSONArray tools = new JSONArray();
        tools.put(tool("list_todos", "列出待办；status 可取 pending/done/deleted/all，默认 all。",
                obj("type", "object", "properties", obj(
                        "status", obj("type", "string", "enum", arr("pending", "done", "deleted", "all"),
                                "description", "待办状态")),
                        "additionalProperties", false)));
        tools.put(tool("add_todo", "新增待办，可选直接设成已完成/已删除。",
                obj("type", "object", "properties", obj(
                        "title", obj("type", "string", "description", "待办标题"),
                        "status", obj("type", "string", "enum", arr("pending", "done", "deleted"))),
                        "required", arr("title"), "additionalProperties", false)));
        tools.put(tool("update_todo", "修改待办标题。",
                obj("type", "object", "properties", obj(
                        "id", obj("type", "integer", "description", "待办 id"),
                        "title", obj("type", "string")),
                        "required", arr("id", "title"), "additionalProperties", false)));
        tools.put(tool("set_todo_status", "把待办标记为 pending/done/deleted。",
                obj("type", "object", "properties", obj(
                        "id", obj("type", "integer"),
                        "status", obj("type", "string", "enum", arr("pending", "done", "deleted"))),
                        "required", arr("id", "status"), "additionalProperties", false)));
        tools.put(tool("delete_todo", "删除待办。permanent=true 为彻底删除，否则移入已删除。",
                obj("type", "object", "properties", obj(
                        "id", obj("type", "integer"),
                        "permanent", obj("type", "boolean", "default", false)),
                        "required", arr("id"), "additionalProperties", false)));
        tools.put(tool("add_elapsed", "给待办累加计时耗时（毫秒）。",
                obj("type", "object", "properties", obj(
                        "id", obj("type", "integer"),
                        "millis", obj("type", "integer", "minimum", 0)),
                        "required", arr("id", "millis"), "additionalProperties", false)));
        tools.put(tool("list_images", "列出 Kindle 上已接收的全部图片及路径、大小、修改时间。",
                obj("type", "object", "properties", obj(), "additionalProperties", false)));
        tools.put(tool("upload_image", "写入图片（base64 编码），接收后与手机上传走同一条保存流程。",
                obj("type", "object", "properties", obj(
                        "filename", obj("type", "string", "description", "文件名，决定扩展名"),
                        "base64", obj("type", "string", "description", "图片内容的 base64"),
                        "mimeType", obj("type", "string", "description", "可选 MIME，如 image/png")),
                        "required", arr("filename", "base64"), "additionalProperties", false)));
        tools.put(tool("delete_image", "删除一张已接收图片，必须 confirm=true 才会执行。",
                obj("type", "object", "properties", obj(
                        "path", obj("type", "string", "description", "list_images 返回的绝对路径"),
                        "confirm", obj("type", "boolean", "description", "安全确认")),
                        "required", arr("path", "confirm"), "additionalProperties", false)));
        tools.put(tool("timer_state", "读取当前计时器状态（含运行中实时剩余时间）。",
                obj("type", "object", "properties", obj(), "additionalProperties", false)));
        tools.put(tool("timer_start", "按指定分钟数开始/继续一个计时器，可关联待办 id。",
                obj("type", "object", "properties", obj(
                        "minutes", obj("type", "integer", "minimum", 1, "maximum", 60),
                        "todoId", obj("type", "integer", "description", "可选：关联待办 id")),
                        "required", arr("minutes"), "additionalProperties", false)));
        tools.put(tool("timer_pause", "暂停当前计时器并保存剩余时间。",
                obj("type", "object", "properties", obj(), "additionalProperties", false)));
        tools.put(tool("timer_resume", "继续当前计时器；若界面未打开则打开并自动继续。",
                obj("type", "object", "properties", obj(), "additionalProperties", false)));
        tools.put(tool("timer_stop", "结束计时并把本次耗时写回关联待办。",
                obj("type", "object", "properties", obj(), "additionalProperties", false)));
        tools.put(tool("calendar_state", "读取台历城市与天气缓存。",
                obj("type", "object", "properties", obj(), "additionalProperties", false)));
        tools.put(tool("calendar_set_city", "设置台历城市（名称 + 经纬度）。",
                obj("type", "object", "properties", obj(
                        "city", obj("type", "string"),
                        "latitude", obj("type", "number"),
                        "longitude", obj("type", "number")),
                        "required", arr("city", "latitude", "longitude"), "additionalProperties", false)));
        tools.put(tool("calendar_refresh", "若台历界面正打开，强制刷新天气。",
                obj("type", "object", "properties", obj(), "additionalProperties", false)));
        tools.put(tool("mcp_set_token", "设置/清空 MCP 访问 token；token 为空表示关闭认证。",
                obj("type", "object", "properties", obj(
                        "token", obj("type", "string", "description", "留空即关闭认证")),
                        "required", arr("token"), "additionalProperties", false)));
        return tools;
    }

    private static JSONObject tool(String name, String description, JSONObject schema) {
        return obj("name", name, "description", description, "inputSchema", schema);
    }

    private static JSONArray resourcesList() {
        return arr(
                resource("kindle://status", "服务状态", "服务运行状态、端口与数据条数"),
                resource("kindle://todos", "全部待办", "三个标签下的全部待办（含时间与累计耗时）"),
                resource("kindle://todos/pending", "未完成待办", "status=pending"),
                resource("kindle://todos/done", "已完成待办", "status=done"),
                resource("kindle://todos/deleted", "已删除待办", "status=deleted"),
                resource("kindle://images", "图片列表", "已接收的图片路径、大小与修改时间"),
                resource("kindle://timer", "计时器", "当前计时器状态与剩余时间"),
                resource("kindle://calendar", "台历", "城市、经纬度与天气缓存"));
    }

    private static JSONObject resource(String uri, String name, String description) {
        return obj("uri", uri, "name", name, "description", description,
                "mimeType", "application/json");
    }

    private static JSONArray resourcesRead(Context context, JSONObject params) {
        String uri = params.optString("uri", "");
        JSONObject data;
        switch (uri) {
            case "kindle://status":
                data = statusResource(context);
                break;
            case "kindle://todos":
                data = obj("todos", allTodos(context));
                break;
            case "kindle://todos/pending":
                data = obj("todos", todoArray(context, "pending"));
                break;
            case "kindle://todos/done":
                data = obj("todos", todoArray(context, "done"));
                break;
            case "kindle://todos/deleted":
                data = obj("todos", todoArray(context, "deleted"));
                break;
            case "kindle://images":
                data = obj("images", imageArray());
                break;
            case "kindle://timer":
                data = TimerActivity.mcpSnapshot(context);
                break;
            case "kindle://calendar":
                data = calendarResource(context);
                break;
            default:
                throw new RpcException(-32602, "Unknown resource: " + uri);
        }
        return arr(obj("uri", uri, "mimeType", "application/json", "text", data.toString()));
    }

    private static JSONObject statusResource(Context context) {
        boolean tokenSet = !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_TOKEN, "").isEmpty();
        return obj(
                "app", "Kindle助手",
                "package", context.getPackageName(),
                "mcpVersion", SERVER_VERSION,
                "protocolVersion", PROTOCOL,
                "endpoint", "POST http://<kindle-ip>:" + UploadService.PORT + ENDPOINT,
                "tokenConfigured", tokenSet,
                "counts", obj("todos", TodoStore.count(context), "images", ImageStore.listImages().size()));
    }

    private static JSONArray allTodos(Context context) {
        JSONArray array = new JSONArray();
        appendTodos(array, TodoStore.list(TodoStore.PENDING));
        appendTodos(array, TodoStore.list(TodoStore.DONE));
        appendTodos(array, TodoStore.list(TodoStore.DELETED));
        return array;
    }

    private static JSONArray todoArray(Context context, String status) {
        int value = TodoStore.PENDING;
        if ("done".equals(status)) {
            value = TodoStore.DONE;
        } else if ("deleted".equals(status)) {
            value = TodoStore.DELETED;
        } else if (!"pending".equals(status) && !"all".equals(status)) {
            throw new RpcException(-32602, "Unknown todo status: " + status);
        }
        JSONArray array = new JSONArray();
        if ("all".equals(status)) {
            return allTodos(context);
        }
        appendTodos(array, TodoStore.list(value));
        return array;
    }

    private static void appendTodos(JSONArray array, List<TodoStore.Todo> todos) {
        for (TodoStore.Todo todo : todos) {
            array.put(obj(
                    "id", todo.id,
                    "title", todo.title,
                    "status", statusName(todo.status),
                    "createdAt", todo.createdAt,
                    "completedAt", todo.completedAt,
                    "deletedAt", todo.deletedAt,
                    "updatedAt", todo.updatedAt,
                    "elapsedMillis", todo.elapsedMillis));
        }
    }

    private static String statusName(int status) {
        if (status == TodoStore.DONE) {
            return "done";
        }
        if (status == TodoStore.DELETED) {
            return "deleted";
        }
        return "pending";
    }

    private static int parseStatus(String value) {
        if ("done".equals(value)) {
            return TodoStore.DONE;
        }
        if ("deleted".equals(value)) {
            return TodoStore.DELETED;
        }
        if (value == null || value.isEmpty() || "pending".equals(value)) {
            return TodoStore.PENDING;
        }
        throw new RpcException(-32602, "Unknown todo status: " + value);
    }

    private static JSONArray imageArray() {
        JSONArray array = new JSONArray();
        for (String path : ImageStore.listImages()) {
            File file = new File(path);
            array.put(obj("path", path, "name", file.getName(),
                    "size", file.length(), "modifiedAt", file.lastModified()));
        }
        return array;
    }

    private static JSONObject calendarResource(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("calendar_state", Context.MODE_PRIVATE);
        JSONObject forecast = null;
        String cached = prefs.getString("forecast", "");
        if (cached != null && !cached.isEmpty()) {
            try {
                forecast = new JSONObject(cached);
            } catch (Exception ignored) {
                forecast = null;
            }
        }
        return obj(
                "city", prefs.getString("city", ""),
                "latitude", Double.longBitsToDouble(prefs.getLong("latitude", 0L)),
                "longitude", Double.longBitsToDouble(prefs.getLong("longitude", 0L)),
                "forecastAt", prefs.getLong("forecastAt", 0L),
                "forecast", forecast);
    }

    private static JSONObject callTool(Context context, JSONObject params) {
        String name = params.optString("name", "");
        JSONObject args = params.optJSONObject("arguments");
        if (args == null) {
            args = new JSONObject();
        }
        try {
            Object data = execute(context, name, args);
            String text = data instanceof String ? (String) data : data.toString();
            return obj("content", arr(obj("type", "text", "text", text)),
                    "isError", false, "resultType", "complete");
        } catch (RpcException e) {
            return toolError(e.getMessage());
        } catch (Exception e) {
            return toolError("调用失败：" + safeMessage(e));
        }
    }

    private static JSONObject toolError(String message) {
        return obj("content", arr(obj("type", "text", "text", message)),
                "isError", true, "resultType", "complete");
    }

    private static Object execute(Context context, String name, JSONObject args) {
        switch (name) {
            case "list_todos":
                return obj("todos", todoArray(context, args.optString("status", "all")));
            case "add_todo": {
                long id = TodoStore.add(context, args.optString("title", ""));
                if (id <= 0) {
                    throw new RpcException(-32602, "标题不能为空");
                }
                String status = args.optString("status", "pending");
                if (!"pending".equals(status)) {
                    TodoStore.setStatus(context, id, parseStatus(status));
                }
                return obj("id", id, "status", status);
            }
            case "update_todo": {
                long id = args.optLong("id", -1);
                boolean ok = TodoStore.update(context, id, args.optString("title", ""));
                return obj("ok", ok, "id", id);
            }
            case "set_todo_status": {
                long id = args.optLong("id", -1);
                boolean ok = TodoStore.setStatus(context, id, parseStatus(args.optString("status", "")));
                return obj("ok", ok, "id", id);
            }
            case "delete_todo": {
                long id = args.optLong("id", -1);
                boolean permanent = args.optBoolean("permanent", false);
                boolean ok = permanent
                        ? TodoStore.remove(context, id)
                        : TodoStore.setStatus(context, id, TodoStore.DELETED);
                return obj("ok", ok, "id", id, "permanent", permanent);
            }
            case "add_elapsed": {
                long id = args.optLong("id", -1);
                long millis = args.optLong("millis", -1);
                if (millis < 0) {
                    throw new RpcException(-32602, "millis 必须 >= 0");
                }
                return obj("ok", TodoStore.addElapsed(context, id, millis), "id", id);
            }
            case "list_images":
                return obj("images", imageArray());
            case "upload_image": {
                String encoded = args.optString("base64", "");
                if (encoded.isEmpty()) {
                    throw new RpcException(-32602, "base64 不能为空");
                }
                if (encoded.length() > MAX_BODY) {
                    throw new RpcException(-32602, "图片过大（base64 上限约 12 MB）");
                }
                byte[] data;
                try {
                    data = Base64.decode(encoded, Base64.DEFAULT);
                } catch (Exception e) {
                    throw new RpcException(-32602, "base64 解码失败");
                }
                try {
                    File saved = ImageStore.save(context, data,
                            args.optString("filename", "image.jpg"),
                            args.optString("mimeType", null));
                    return obj("ok", true, "path", saved.getAbsolutePath());
                } catch (java.io.IOException e) {
                    throw new RpcException(-32603, "图片保存失败：" + safeMessage(e));
                }
            }
            case "delete_image": {
                if (!args.optBoolean("confirm", false)) {
                    throw new RpcException(-32602, "删除图片需要 confirm=true");
                }
                boolean ok = ImageStore.delete(context, args.optString("path", ""));
                return obj("ok", ok);
            }
            case "timer_state":
                return TimerActivity.mcpSnapshot(context);
            case "timer_start": {
                int minutes = (int) args.optLong("minutes", 10);
                if (minutes < 1 || minutes > 60) {
                    throw new RpcException(-32602, "minutes 必须在 1~60 之间");
                }
                long id = args.optLong("todoId", -1);
                TimerActivity.mcpStart(context, id, minutes);
                return obj("started", true, "minutes", minutes, "todoId", id);
            }
            case "timer_pause":
                return obj("ok", TimerActivity.mcpPause());
            case "timer_resume":
                return obj("ok", TimerActivity.mcpResume(context));
            case "timer_stop":
                return obj("ok", TimerActivity.mcpStop(context));
            case "calendar_state":
                return calendarResource(context);
            case "calendar_set_city": {
                String city = args.optString("city", "");
                if (city.isEmpty()) {
                    throw new RpcException(-32602, "city 不能为空");
                }
                boolean ok = CalendarActivity.mcpSetCity(context, city,
                        args.optDouble("latitude", Double.NaN),
                        args.optDouble("longitude", Double.NaN));
                return obj("ok", ok, "city", city);
            }
            case "calendar_refresh":
                return obj("ok", CalendarActivity.mcpRefresh());
            case "mcp_set_token": {
                String token = args.optString("token", "").trim();
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putString(KEY_TOKEN, token).apply();
                return obj("ok", true, "tokenConfigured", !token.isEmpty(),
                        "note", token.isEmpty() ? "已关闭 MCP 认证"
                                : "已开启认证，后续请求需带 Authorization: Bearer " + token);
            }
            default:
                throw new RpcException(-32601, "Unknown tool: " + name);
        }
    }

    private static String error(Object id, int code, String message) {
        return obj("jsonrpc", "2.0", "id", id,
                "error", obj("code", code, "message", message)).toString();
    }

    private static JSONObject obj(Object... keyValues) {
        JSONObject object = new JSONObject();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            Object value = keyValues[i + 1];
            if (value == null) {
                continue;
            }
            try {
                object.put((String) keyValues[i], value);
            } catch (Exception ignored) {
                // 单个字段写不进去不影响其余字段。
            }
        }
        return object;
    }

    private static JSONArray arr(Object... values) {
        JSONArray array = new JSONArray();
        for (Object value : values) {
            array.put(value);
        }
        return array;
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isEmpty() ? e.getClass().getSimpleName() : message;
    }

    private static final class RpcException extends RuntimeException {
        final int code;

        RpcException(int code, String message) {
            super(message);
            this.code = code;
        }
    }
}
