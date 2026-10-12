"""第 9 课示例：用 requests 访问网络。

全部请求都加上了 timeout，并且每一步都能优雅降级 ——
没有网络时会打印提示而不是抛出异常，方便直接运行体验。
"""

import json
import os
import time

try:
    import requests
except ImportError:
    print("没有安装 requests。")
    print("请到「软件包」页搜索 requests 并点「安装」，然后重新运行。")
    raise SystemExit(0)

print(f"requests {requests.__version__}")
BASE = "https://httpbin.org"

print("\n=== 1. 最简单的 GET ===")
try:
    r = requests.get(f"{BASE}/get", timeout=10)
    print("status_code:", r.status_code, " ok:", r.ok)
    print("Content-Type:", r.headers.get("Content-Type"))
    data = r.json()
    print("你的出口 IP:", data.get("origin"))
    print("User-Agent :", data.get("headers", {}).get("User-Agent"))
except requests.RequestException as e:
    print("网络不可用或超时：", type(e).__name__, e)
    print("（示例其余部分将跳过）")
    raise SystemExit(0)

print("\n=== 2. 查询参数会被自动编码 ===")
r = requests.get(f"{BASE}/get",
                 params={"q": "python 教程", "page": 1},
                 headers={"User-Agent": "PyDroidLab/1.0"},
                 timeout=10)
print("完整 URL :", r.url)
print("解析后的参数:", r.json()["args"])

print("\n=== 3. POST 表单 ===")
r = requests.post(f"{BASE}/post", data={"name": "小明", "age": 20}, timeout=10)
print("服务端收到的表单:", r.json()["form"])

print("\n=== 4. POST JSON ===")
r = requests.post(f"{BASE}/post",
                  json={"name": "小明", "tags": ["python", "android"]},
                  timeout=10)
print("服务端收到的 JSON:", r.json()["json"])

print("\n=== 5. 状态码处理 ===")
for code in [200, 404, 500]:
    r = requests.get(f"{BASE}/status/{code}", timeout=10)
    print(f"  /status/{code} -> status_code={r.status_code}  ok={r.ok}")
    try:
        r.raise_for_status()
        print("    raise_for_status() 通过")
    except requests.HTTPError as e:
        print("    raise_for_status() 抛出 HTTPError:", e)

print("\n=== 6. 错误处理（访问一个不存在的域名）===")


def fetch_json(url, timeout=8):
    """任何异常都转成可读的文字，绝不向外抛。"""
    try:
        r = requests.get(url, timeout=timeout)
        r.raise_for_status()
        return r.json()
    except requests.Timeout:
        return {"error": "请求超时"}
    except requests.ConnectionError as e:
        return {"error": f"连接失败：{type(e).__name__}"}
    except requests.HTTPError as e:
        return {"error": f"HTTP {e.response.status_code}"}
    except ValueError:
        return {"error": "返回内容不是合法 JSON"}
    except requests.RequestException as e:
        return {"error": f"其它异常：{type(e).__name__}"}


print("正常站点 :", fetch_json(f"{BASE}/get").get("origin", "?"), "← 出口 IP")
print("坏域名   :", fetch_json("https://this-host-does-not-exist.invalid/get"))
print("404 站点 :", fetch_json(f"{BASE}/status/404"))

print("\n=== 7. 带重试的请求 ===")


def get_json_with_retry(url, retries=3, backoff=1.5):
    last_error = None
    for attempt in range(1, retries + 1):
        try:
            r = requests.get(url, timeout=8)
            r.raise_for_status()
            print(f"  第 {attempt} 次尝试成功")
            return r.json()
        except requests.RequestException as e:
            last_error = e
            wait = backoff ** attempt
            print(f"  第 {attempt} 次失败（{type(e).__name__}），{wait:.2f}s 后重试")
            time.sleep(wait)
    print("  最终失败:", last_error)
    return None


result = get_json_with_retry(f"{BASE}/get", retries=2)
print("结果 origin =", (result or {}).get("origin"))

print("\n=== 8. 下载二进制文件 ===")
here = os.path.dirname(os.path.abspath(__file__))
png_path = os.path.join(here, "downloaded_image.png")
try:
    r = requests.get(f"{BASE}/image/png", timeout=30)
    r.raise_for_status()
    with open(png_path, "wb") as f:
        f.write(r.content)
    print(f"已下载 {len(r.content)} 字节 -> {png_path}")
    print("可以到「文件」页看到这个文件。")
except requests.RequestException as e:
    print("下载失败：", type(e).__name__, e)

print("\n=== 9. 流式下载（大文件建议这样写）===")
try:
    total = 0
    with requests.get(f"{BASE}/bytes/20480", stream=True, timeout=30) as r:
        r.raise_for_status()
        for chunk in r.iter_content(chunk_size=4096):
            total += len(chunk)
    print(f"流式下载完成，共 {total} 字节")
except requests.RequestException as e:
    print("流式下载失败：", type(e).__name__, e)

print("\n=== 10. 把响应写进 JSON 文件 ===")
out = os.path.join(here, "httpbin_result.json")
try:
    data = requests.get(f"{BASE}/get", timeout=10).json()
    with open(out, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    print("已保存:", out)
except requests.RequestException as e:
    print("保存失败：", type(e).__name__, e)

print("\n=== 示例结束 ===")
