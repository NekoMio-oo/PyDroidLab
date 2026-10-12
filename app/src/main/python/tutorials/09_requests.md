# 第 9 课：用 requests 访问网络

## 本课目标

- 用 `requests` 发 GET / POST 请求
- 处理 JSON 响应、超时与常见错误
- 知道 App 需要的网络权限与注意事项

## 9.1 权限说明

PyDroidLab 的清单里已经声明了：

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

所以脚本可以直接联网，不需要额外授权弹窗。

## 9.2 requests 是否已装好

`requests` 是**构建期预装**的纯 Python 包，装好 App 就能用：

```python
import requests
print(requests.__version__)
```

如果提示 `ModuleNotFoundError`，到「软件包」页装一次即可。

## 9.3 最简单的 GET

```python
import requests

r = requests.get("https://httpbin.org/get", timeout=10)

print(r.status_code)          # 200
print(r.headers["Content-Type"])
print(r.text[:200])           # 文本
data = r.json()               # 自动解析 JSON
print(data["headers"]["User-Agent"])
```

要点：

- **一定要写 `timeout`**。默认没有超时，网络卡住时脚本会一直挂着
- `r.status_code` 是数字状态码；`r.ok` 是「2xx 为 True」的布尔值
- `r.text` 是解码后的字符串；`r.content` 是原始 bytes（下载二进制时用）
- `r.json()` 会解析 JSON，内容不是 JSON 会抛 `ValueError`

## 9.4 查询参数与请求头

```python
params = {"q": "python", "page": 1}
headers = {"User-Agent": "PyDroidLab/1.0"}

r = requests.get("https://httpbin.org/get", params=params, headers=headers, timeout=10)
print(r.url)        # 拼好的完整 URL
print(r.json()["args"])
```

`params` 会自动做 URL 编码，比手写 `?a=b&c=d` 安全。

## 9.5 POST

```python
# 表单
r = requests.post("https://httpbin.org/post",
                  data={"name": "小明", "age": 20}, timeout=10)

# JSON（会自动加 Content-Type: application/json）
r = requests.post("https://httpbin.org/post",
                  json={"name": "小明", "tags": ["python"]}, timeout=10)

print(r.json()["json"])
```

## 9.6 错误处理

```python
import requests

def fetch(url):
    try:
        r = requests.get(url, timeout=10)
        r.raise_for_status()          # 4xx / 5xx 直接抛异常
        return r.json()
    except requests.Timeout:
        print("超时了，稍后重试")
    except requests.ConnectionError:
        print("连不上，检查网络")
    except requests.HTTPError as e:
        print(f"HTTP 错误：{e.response.status_code}")
    except ValueError:
        print("返回内容不是合法 JSON")
    except requests.RequestException as e:
        print("其它请求异常：", e)
    return None
```

异常层次：`RequestException` 是所有 requests 异常的父类，
`Timeout`、`ConnectionError`、`HTTPError` 都是它的子类。

## 9.7 下载文件

```python
url = "https://httpbin.org/image/png"
r = requests.get(url, timeout=30)
r.raise_for_status()

with open("downloaded.png", "wb") as f:      # 二进制模式
    f.write(r.content)

import os
print("大小:", os.path.getsize("downloaded.png"), "字节")
```

大文件建议流式下载：

```python
with requests.get(url, stream=True, timeout=30) as r:
    r.raise_for_status()
    with open("big.bin", "wb") as f:
        for chunk in r.iter_content(chunk_size=8192):
            f.write(chunk)
```

## 9.8 写一个带重试的请求

```python
import time
import requests

def get_json(url, retries=3, backoff=1.5):
    last = None
    for attempt in range(1, retries + 1):
        try:
            r = requests.get(url, timeout=8)
            r.raise_for_status()
            return r.json()
        except requests.RequestException as e:
            last = e
            wait = backoff ** attempt
            print(f"第 {attempt} 次失败（{type(e).__name__}），{wait:.1f}s 后重试")
            time.sleep(wait)
    print("最终失败:", last)
    return None
```

## 9.9 注意事项

- 不要在**主线程之外没影响**，但别在循环里无节制地请求，容易被限流
- 有些网站会校验 `User-Agent`，被 403 时试着加上
- 明文 HTTP 在 App 里被禁用（`usesCleartextTraffic=false`），请使用 `https://`
- 部分站点在国内访问不稳定，写代码时最好都能优雅降级

## 9.10 动手练习

1. 请求 `https://httpbin.org/get` 并打印出你的出口 IP。
2. 封装一个 `get_json(url)`，超时与断网都能给出可读的错误提示。
3. 下载 `https://httpbin.org/image/png` 到项目目录，然后到「文件」页确认文件存在。

## 课程结束

到这里 9 课就结束了。建议回到「文件」页，把练习都存成独立的小脚本，
再用「终端」页的 `ls` / `cat` / `run` 习惯一下命令行操作。

---

### 本课示例

`09_requests_example.py` 会依次演示 GET / 参数 / POST / 错误处理 / 下载，
全部使用 `httpbin.org`（若无网络会优雅降级，不会崩）。
