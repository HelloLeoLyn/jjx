#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
测试数据运行器 · 共享底座（dev-20261008-023）

设计口径：
- 目标：当前开发库；测试数据统一带 [TST] 标记，便于识别与清理。
- 幂等：按“标记 + 业务键”检测，已存在则复用/跳过，重复执行不产生重复单。
- 不依赖 db-clean-test-data；提供按标记清理的扩展点。

用法：
  export JJX_BASE_URL=http://127.0.0.1:8080   # 默认
  export JJX_USER=admin  JJX_PASS=******  JJX_TENANT=1
  python3 scripts/testdata/module1_sales.py [--dry-run]

约定：
- 后端统一返回 Result{ code, data, message }，code==200 视为成功。
- 鉴权：登录后把 token 放到请求头 `token`（与前端一致）。
"""
import os
import sys
import json
import time
import urllib.request
import urllib.error
import urllib.parse

TST = "[TST]"

# ── 配置（env 注入，避免写死凭据/地址）──────────────────────────────
BASE_URL = os.environ.get("JJX_BASE_URL", "http://127.0.0.1:8080").rstrip("/")
USER = os.environ.get("JJX_USER", "")
PASS = os.environ.get("JJX_PASS", "")
TENANT = os.environ.get("JJX_TENANT", "1")
DRY_RUN = "--dry-run" in sys.argv

_TOKEN = None

# 本机开发后端：显式绕过系统代理（HTTP_PROXY 会把 127.0.0.1 也走代理 → 503）
_OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))


class ApiError(Exception):
    pass


def _http(method, path, body=None, token=None):
    url = BASE_URL + path
    data = None
    headers = {"Content-Type": "application/json"}
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    if token:
        headers["token"] = token
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with _OPENER.open(req, timeout=30) as resp:
            raw = resp.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        raise ApiError(f"HTTP {e.code} {method} {path}: {e.read().decode('utf-8', 'replace')[:300]}")
    except urllib.error.URLError as e:
        raise ApiError(f"连接失败 {method} {path}: {e}")
    try:
        return json.loads(raw) if raw else {}
    except json.JSONDecodeError:
        raise ApiError(f"非 JSON 响应 {method} {path}: {raw[:200]}")


def login():
    """登录取 token。凭据从 env 取。"""
    global _TOKEN
    if _TOKEN:
        return _TOKEN
    if not USER or not PASS:
        raise ApiError("缺少凭据：请设置环境变量 JJX_USER / JJX_PASS")
    res = _http("POST", "/sessions/auth", {
        "username": USER, "password": PASS,
        "tenantId": int(TENANT), "rememberMe": False,
    })
    data = _unwrap(res, "登录")
    # token 字段名容错
    tok = data.get("token") if isinstance(data, dict) else None
    if not tok:
        # 有些实现把 token 放在 data.tokenValue / data.accessToken
        for k in ("tokenValue", "accessToken", "value"):
            if isinstance(data, dict) and data.get(k):
                tok = data[k]
                break
    if not tok:
        raise ApiError(f"登录返回没有 token: {data}")
    _TOKEN = tok
    log(f"登录成功：{USER}")
    return tok


def _unwrap(res, what):
    """拆 Result 信封，返回 data（code==200 视为成功）。"""
    if not isinstance(res, dict):
        return res
    code = res.get("code")
    if code is not None and code != 200:
        raise ApiError(f"{what} 失败 code={code} msg={res.get('message') or res.get('msg')}")
    return res.get("data", res)


def call(method, path, body=None, what=None):
    """带 token 调接口，返回 data；DRY_RUN 只打印不执行。"""
    what = what or f"{method} {path}"
    if DRY_RUN:
        log(f"[dry-run] {method} {path}  {json.dumps(body, ensure_ascii=False) if body else ''}")
        return {"__dry_run__": True}
    res = _http(method, path, body, token=login())
    return _unwrap(res, what)


def try_call(method, path, body=None, what=None):
    """容错版：失败只记一行日志、返回 None（用于幂等重跑时跳过已完成步骤）。"""
    try:
        return call(method, path, body, what)
    except ApiError as e:
        log(f"（跳过）{what or path}: {e}")
        return None


def get(path, params=None, what=None):
    if params:
        q = "&".join(f"{k}={urllib.parse.quote(str(v))}" for k, v in params.items() if v is not None)
        path = f"{path}?{q}"
    return call("GET", path, None, what)


def get_list(path, params=None) -> list:
    """取分页列表的 records（兼容 records / list / 数组）。"""
    data = get(path, params)
    if isinstance(data, list):
        return data
    if isinstance(data, dict):
        for k in ("records", "list", "rows", "items"):
            v = data.get(k)
            if isinstance(v, list):
                return v
    return []


def find_first(path, params, pred):
    for row in get_list(path, params):
        try:
            if pred(row):
                return row
        except Exception:
            continue
    return None


def get_customer():
    """复用**现有客户**（不新建）。JJX_TST_CUSTOMER_ID 优先；否则优先**真实客户**（跳过 [TST] 测试客户）。"""
    cid = os.environ.get("JJX_TST_CUSTOMER_ID")
    if cid:
        row = get(f"/sales/customers/{int(cid)}")
        name = row.get("customerName") if isinstance(row, dict) else None
        log(f"使用客户 id={cid} {name}")
        return int(cid), name
    rows = get_list("/sales/customers", {"current": 1, "pageSize": 50})
    real = [c for c in rows if not str(c.get("customerName", "")).startswith(TST)]
    pool = real or rows
    if not pool:
        log("⚠ 库里没有客户，无法继续（本脚本不新建客户）")
        return None, None
    pick = pool[0]
    log(f"使用客户 id={pick.get('customerId')} {pick.get('customerName')}")
    return pick.get("customerId"), pick.get("customerName")


_LOGGED = []


def log(msg):
    line = f"[{time.strftime('%H:%M:%S')}] {msg}"
    print(line)
    _LOGGED.append(line)


def summary():
    print("\n===== 结果 =====")
    for l in _LOGGED:
        print(l)


def mask(name):
    return f"{TST}{name}"
