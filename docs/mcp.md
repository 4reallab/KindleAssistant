# Kindle 助手 AI 助手协议接口（MCP）

## 为什么选 MCP

给 AI 助手开放「读 + 写」能力，可选方案主要有三种：

| 方案 | 适合场景 | 结论 |
| --- | --- | --- |
| **MCP（Model Context Protocol）** | 专为 LLM/AI 助手连接外部数据与工具设计，原生区分 Resources（读）和 Tools（写），Claude、WorkBuddy、Cursor、ChatGPT 连接器等普遍支持 | ✅ 选用 |
| REST + OpenAPI | 给人写的通用 HTTP API，AI 需要额外解析 OpenAPI 并自行构造调用 | 可作为兼容层，但不是 AI 原生协议 |
| 自定义 JSON-RPC / WebSocket | 完全自控，但客户端要逐个适配，生态为零 | 不推荐 |

MCP 的消息层就是 **JSON-RPC 2.0**；传输层我们采用 **Streamable HTTP**：客户端把单条请求 POST 到 `/mcp`，服务端返回单个 JSON 响应。这样可以直接复用 Kindle 助手已有的局域网 HTTP 服务（端口 8088），不需要在 Android 8.1 上跑 stdio 子进程，也不需要新开端口。

协议版本：`2025-06-18`（同时接受 `2025-11-25` 的客户端）。

## 端点

```
POST http://<kindle-ip>:8088/mcp
Content-Type: application/json
Accept: application/json, text/event-stream
```

- 请求体：单条 JSON-RPC 2.0 请求。
- 响应体：单条 JSON-RPC 2.0 响应（`Content-Type: application/json`），带 `MCP-Protocol-Version` 头。
- 客户端通知（无 `id`）返回 `202`。
- 支持方法：`initialize`、`ping`、`tools/list`、`tools/call`、`resources/list`、`resources/read`、`prompts/list`、`logging/setLevel`。
- 不维护会话状态，不要求 `Mcp-Session-Id`，方便 AI 客户端随时重连。

## 读取：Resources

| URI | 内容 |
| --- | --- |
| `kindle://status` | 服务状态、包名、MCP 版本、数据条数 |
| `kindle://todos` | 三个标签下的全部待办（含添加/完成/删除时间与累计耗时） |
| `kindle://todos/pending` | 未完成待办 |
| `kindle://todos/done` | 已完成待办 |
| `kindle://todos/deleted` | 已删除待办 |
| `kindle://images` | 已接收图片的路径、大小、修改时间 |
| `kindle://timer` | 计时器状态；界面打开时为实时剩余时间 |
| `kindle://calendar` | 台历城市、经纬度、天气缓存 |

`resources/read` 返回 `contents[0].text`，内容为该资源的 JSON 字符串。

## 写入：Tools

共 18 个工具，全部返回标准 MCP `content` 数组。

| 分类 | 工具 |
| --- | --- |
| 待办 | `add_todo`、`update_todo`、`set_todo_status`、`delete_todo`（默认软删除，`permanent=true` 彻底删除）、`add_elapsed`、`list_todos` |
| 图片 | `list_images`、`upload_image`（base64 写入）、`delete_image`（必须 `confirm=true`） |
| 计时 | `timer_state`、`timer_start`、`timer_pause`、`timer_resume`、`timer_stop`（结束时会把耗时写回关联待办） |
| 台历 | `calendar_state`、`calendar_set_city`、`calendar_refresh` |
| 安全 | `mcp_set_token`（`token` 留空即关闭认证） |

调用示例：

```json
{"jsonrpc":"2.0","id":7,"method":"tools/call",
 "params":{"name":"add_todo","arguments":{"title":"让 AI 加的待办"}}}
```

```json
{"jsonrpc":"2.0","id":8,"method":"resources/read",
 "params":{"uri":"kindle://todos"}}
```

工程内自检脚本：

```bash
python scripts/test_mcp.py http://192.168.31.192:8088
```

它只创建并彻底删除一条临时待办，不改用户原有数据。

## 认证（可选）

默认不启用认证，局域网内可直接访问。建议开启：

1. 先调用一次 `mcp_set_token`，设置一个随机 token；
2. 之后所有 `/mcp` 请求都需要在 HTTP 头带 `Authorization: Bearer <token>`；
3. 用 `mcp_set_token` 传空字符串即可关闭认证。

未带或带错 token 返回 `401`，并带 `WWW-Authenticate: Bearer`。

> 这个接口能读写设备上的全部数据，请把 Kindle 放在可信局域网内；需要公网访问时请自行加反向代理与 TLS，不要直接把 8088 端口暴露到公网。

## AI 客户端配置

在手机或电脑浏览器打开 Kindle 主页 `http://192.168.31.192:8088/`，点「AI 助手操作指南」进入 `/ai.html`。
页面里的 MCP 地址按设备当前局域网 IP 自动生成，并提供一键复制；配置操作都在**电脑端 AI 助手**里完成。

- **WorkBuddy（推荐）**：复制页面第 1 步的整段指令发给 WorkBuddy，它会自动写入 MCP 配置（不覆盖已有服务器），
  然后按提示在连接器管理里信任 / 启用 `kindle-assistant`，重开一个对话即可。
- **豆包**：普通豆包 App 不支持 MCP，需使用**豆包工作版**（桌面端）**手动添加**：在 MCP 服务器管理里
  选 Streamable HTTP，名称填 `kindle-assistant`，URL 填页面给出的地址，保存后打开开关。
- **其他客户端**：同样选 Streamable HTTP / HTTP 传输，填同一个 URL。

手写配置文件时，把下面内容合并到 `~/.workbuddy/mcp.json` 的 `mcpServers` 中，然后在连接器管理页信任并启用：

```json
{
  "mcpServers": {
    "kindle-assistant": {
      "url": "http://192.168.31.192:8088/mcp",
      "headers": {
        "Authorization": "Bearer <你的 token>"
      }
    }
  }
}
```

未启用 token 时删掉 `headers` 即可。

页面第 3 步列出了可直接发给 AI 助手的示例指令，例如「列出我 Kindle 上所有未完成的待办」
「添加一条待办：明天上午十点交材料」「把「单词书写」标记为已完成」。

## 限制

- 单请求体上限 16 MB，因此 `upload_image` 的 base64 约 12 MB 以内。
- `timer_pause/resume/stop` 在计时界面未打开时：暂停/继续依赖持久化的暂停状态；`timer_stop` 会把保存的剩余时间折算成耗时写回待办。
- `calendar_refresh` 只在台历界面正打开时生效；未打开时写入城市会在下次进入台历时自动读取并刷新。
- 图片删除只允许删本应用管理的 `Pictures/KindlePhotoDrop` 及兼容目录，避免误删其他相册文件。
