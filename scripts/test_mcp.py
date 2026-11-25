#!/usr/bin/env python3
"""Kindle 助手 MCP 接口自检脚本。

用法：python scripts/test_mcp.py [http://192.168.31.192:8088]
只读 + 创建/彻底删除一条临时待办，不改动用户原有数据。
"""
import json
import sys
import urllib.request

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://192.168.31.192:8088"
ENDPOINT = BASE.rstrip("/") + "/mcp"
TITLE = "MCP自检临时项"


def call(method, params=None, req_id=None):
    payload = {"jsonrpc": "2.0", "method": method}
    if params is not None:
        payload["params"] = params
    if req_id is not None:
        payload["id"] = req_id
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(
        ENDPOINT,
        data=data,
        headers={"Content-Type": "application/json", "Accept": "application/json, text/event-stream"},
    )
    with urllib.request.urlopen(request, timeout=15) as response:
        raw = response.read().decode("utf-8")
        return response.status, (json.loads(raw) if raw else None)


def tool(name, arguments=None):
    status, body = call("tools/call", {"name": name, "arguments": arguments or {}}, req_id=99)
    assert status == 200, (name, status, body)
    content = body["result"]["content"][0]["text"]
    return json.loads(content)


def main():
    status, body = call("initialize", {
        "protocolVersion": "2025-06-18",
        "capabilities": {},
        "clientInfo": {"name": "mcp-selftest", "version": "1.0"},
    }, req_id=1)
    assert status == 200 and body["result"]["protocolVersion"] in ("2025-06-18", "2025-11-25"), body
    print("initialize:", body["result"]["serverInfo"])

    status, _ = call("notifications/initialized")
    assert status == 202, status
    print("notification: 202")

    _, body = call("tools/list", req_id=2)
    names = [t["name"] for t in body["result"]["tools"]]
    assert "add_todo" in names and "timer_state" in names, names
    print(f"tools/list: {len(names)} tools")

    _, body = call("resources/list", req_id=3)
    uris = [r["uri"] for r in body["result"]["resources"]]
    assert "kindle://todos" in uris and "kindle://calendar" in uris, uris
    print(f"resources/list: {len(uris)} resources")

    _, body = call("resources/read", {"uri": "kindle://todos"}, req_id=4)
    before = json.loads(body["result"]["contents"][0]["text"])["todos"]
    print(f"todos before: {len(before)}")

    created = tool("add_todo", {"title": TITLE})
    todo_id = created["id"]
    print("add_todo:", created)

    _, body = call("resources/read", {"uri": "kindle://todos"}, req_id=5)
    todos = json.loads(body["result"]["contents"][0]["text"])["todos"]
    assert any(t["id"] == todo_id for t in todos), "新建待办未在资源中出现"
    print("resource reflects new todo:", todo_id)

    deleted = tool("delete_todo", {"id": todo_id, "permanent": True})
    assert deleted["ok"], deleted
    _, body = call("resources/read", {"uri": "kindle://todos"}, req_id=6)
    after = json.loads(body["result"]["contents"][0]["text"])["todos"]
    assert len(after) == len(before), (len(before), len(after))
    print("permanent delete ok, todos restored:", len(after))

    for name in ("timer_state", "calendar_state", "list_images", "list_todos"):
        result = tool(name)
        print(f"{name}: ok ({list(result)[:4]})")

    print("MCP SELFTEST PASSED")


if __name__ == "__main__":
    main()
