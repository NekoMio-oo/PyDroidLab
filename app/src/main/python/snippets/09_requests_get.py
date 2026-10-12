"""requests 常用封装：带超时、重试与统一错误处理

适合：任何要访问 HTTP 接口的脚本。复制过去改 URL 即可。
要点：一定要设 timeout；raise_for_status() 把 4xx/5xx 变成异常。
"""

import json
import time
from pathlib import Path

try:
    import requests
except ImportError:
    print("还没安装 requests。请到「软件包」页搜索 requests 安装后再运行。")
    raise SystemExit(0)

HERE = Path(__file__).parent

DEFAULT_TIMEOUT = 10
DEFAULT_HEADERS = {
    "User-Agent": "PyDroidLab/1.0 (+https://example.invalid)",
    "Accept": "application/json, text/plain, */*",
}


class ApiError(Exception):
    """把各种 requests 异常统一成一种，方便上层处理。"""


def request_json(method, url, *, retries=3, backoff=1.6, timeout=DEFAULT_TIMEOUT,
                 headers=None, **kwargs):
    """发起请求并返回解析后的 JSON。

    失败时抛 ApiError（带可读的原因），调用方只需要 try 一次。
    """
    merged_headers = dict(DEFAULT_HEADERS)
    if headers:
        merged_headers.update(headers)

    last_error = None
    for attempt in range(1, retries + 1):
        try:
            resp = requests.request(
                method, url,
                timeout=timeout,
                headers=merged_headers,
                **kwargs,
            )
            resp.raise_for_status()
            return resp.json()

        except requests.Timeout as e:
            last_error = f"请求超时（{timeout}s）"
            last_error += f"：{e}"
        except requests.ConnectionError as e:
            last_error = f"连接失败：{type(e).__name__}"
        except requests.HTTPError as e:
            status = e.response.status_code
            # 4xx 是调用方的问题，重试没意义，直接失败
            if 400 <= status < 500:
                raise ApiError(f"HTTP {status}（客户端错误，不重试）") from e
            last_error = f"HTTP {status}（服务端错误）"
        except ValueError as e:
            raise ApiError(f"返回内容不是合法 JSON：{e}") from e
        except requests.RequestException as e:
            last_error = f"{type(e).__name__}: {e}"

        if attempt < retries:
            wait = backoff ** attempt
            print(f"  · 第 {attempt} 次失败（{last_error}），{wait:.2f}s 后重试")
            time.sleep(wait)

    raise ApiError(f"重试 {retries} 次仍失败：{last_error}")


def download(url, dest, *, timeout=60, chunk_size=8192):
    """流式下载到本地文件，返回 (路径, 字节数)。"""
    dest = Path(dest)
    total = 0
    with requests.get(url, stream=True, timeout=timeout) as resp:
        resp.raise_for_status()
        with open(dest, "wb") as f:
            for chunk in resp.iter_content(chunk_size=chunk_size):
                if chunk:
                    f.write(chunk)
                    total += len(chunk)
    return dest, total


if __name__ == "__main__":
    BASE = "https://httpbin.org"

    print("=== 1. GET 并解析 JSON ===")
    try:
        data = request_json("GET", f"{BASE}/get", params={"from": "pydroidlab"})
        print("出口 IP :", data.get("origin"))
        print("收到参数 :", data.get("args"))
    except ApiError as e:
        print("失败：", e)

    print("\n=== 2. POST JSON ===")
    try:
        data = request_json("POST", f"{BASE}/post",
                            json={"name": "小明", "score": 92})
        print("服务端回显 :", data.get("json"))
    except ApiError as e:
        print("失败：", e)

    print("\n=== 3. 4xx 不重试 ===")
    try:
        request_json("GET", f"{BASE}/status/404", retries=3)
    except ApiError as e:
        print("如期失败：", e)

    print("\n=== 4. 5xx 会重试 ===")
    try:
        request_json("GET", f"{BASE}/status/503", retries=2, backoff=1.2)
    except ApiError as e:
        print("最终失败：", e)

    print("\n=== 5. 域名不存在 ===")
    try:
        request_json("GET", "https://no-such-host.invalid/api")
    except ApiError as e:
        print("如期失败：", e)

    print("\n=== 6. 下载文件 ===")
    try:
        path, size = download(f"{BASE}/image/png", HERE / "snippet_downloaded.png")
        print(f"已下载 {size} 字节 -> {path.name}")
        path.unlink(missing_ok=True)
        print("（示例文件已清理）")
    except requests.RequestException as e:
        print("下载失败：", type(e).__name__, e)

    print("\n=== 7. 把结果存成 JSON ===")
    try:
        data = request_json("GET", f"{BASE}/get")
        out = HERE / "snippet_last_response.json"
        out.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
        print("已保存:", out.name)
        out.unlink(missing_ok=True)
    except ApiError as e:
        print("失败：", e)
