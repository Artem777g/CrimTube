#!/usr/bin/env python3
"""
CrimTube mux server.

Берёт видео и аудио с публичного Invidious-инстанса, склеивает их ffmpeg
в обычный MP4 и отдаёт телефону один файл. Так исчезает рассинхрон звука
на Windows 10 Mobile.

Запуск:
    python mux_server.py --ffmpeg C:\\path\\ffmpeg.exe --port 8765
"""

import argparse
import json
import os
import re
import subprocess
import threading
import time
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

DEFAULT_INSTANCES = [
    "http://5.253.247.182:3000",
    "http://82.69.47.60:31008",
    "http://41.193.14.3:3000",
    "http://73.26.148.72:3000",
    "http://14.225.212.141:3000",
    "http://60.241.100.198:31008",
]

CACHE_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "cache")
FFMPEG = "ffmpeg"
LOCK = threading.Lock()
JOBS = {}


def http_json(url, timeout=12):
    req = urllib.request.Request(url, headers={"User-Agent": "CrimTube-mux/1.0"})
    with urllib.request.urlopen(req, timeout=timeout) as response:
        return json.loads(response.read().decode("utf-8", "replace"))


def resolve(base, url):
    if not url:
        return None
    if url.startswith("http://") or url.startswith("https://"):
        return url
    return base.rstrip("/") + (url if url.startswith("/") else "/" + url)


def url_ok(url):
    """Проверяет, что сервер реально отдаёт медиаданные, а не 403/HTML."""
    if not url:
        return False
    try:
        request = urllib.request.Request(url, headers={
            "User-Agent": "Mozilla/5.0",
            "Range": "bytes=0-1023",
        })
        with urllib.request.urlopen(request, timeout=10) as response:
            if response.status not in (200, 206):
                return False
            content_type = response.headers.get("Content-Type", "")
            if content_type and not any(kind in content_type for kind in ("video", "audio", "octet-stream")):
                return False
            return len(response.read(16)) > 0
    except Exception:
        return False


def find_tracks(video_id, height):
    last = None
    for base in DEFAULT_INSTANCES:
        try:
            data = http_json(base + "/api/v1/videos/" + urllib.parse.quote(video_id) + "?local=true")
        except Exception as exc:
            last = exc
            continue

        video = None
        audio = None
        for stream in data.get("adaptiveFormats", []):
            kind = stream.get("type", "")
            if "video/mp4" not in kind or "avc1" not in kind:
                continue
            label = stream.get("qualityLabel") or ""
            match = re.match(r"(\d+)", label)
            if not match:
                continue
            stream_height = int(match.group(1))
            if stream_height < height:
                continue
            if video is None or stream_height < video[0]:
                video = (stream_height, stream)
        for stream in data.get("adaptiveFormats", []):
            kind = stream.get("type", "")
            if "audio/mp4" in kind:
                bitrate = stream.get("bitrate", 0) or 0
                if audio is None or bitrate > audio[0]:
                    audio = (bitrate, stream)
        if video and audio:
            video_url = resolve(base, video[1].get("url"))
            audio_url = resolve(base, audio[1].get("url"))
            if url_ok(video_url) and url_ok(audio_url):
                return base, video[1], audio[1]
            last = RuntimeError("инстанс не отдаёт медиаданные")
        else:
            last = RuntimeError("нет подходящих дорожек")
    raise RuntimeError(str(last) if last else "нет инстанса")


def cache_path(video_id, height):
    return os.path.join(CACHE_DIR, "%s_%dp.mp4" % (video_id, height))


def mux(video_id, height):
    path = cache_path(video_id, height)
    if os.path.exists(path) and os.path.getsize(path) > 0:
        return path

    with LOCK:
        if os.path.exists(path) and os.path.getsize(path) > 0:
            return path

        os.makedirs(CACHE_DIR, exist_ok=True)
        base, video, audio = find_tracks(video_id, height)
        video_url = resolve(base, video.get("url"))
        audio_url = resolve(base, audio.get("url"))
        temp = path + ".part"

        args = [
            FFMPEG, "-y", "-hide_banner", "-loglevel", "warning",
            "-user_agent", "Mozilla/5.0", "-i", video_url,
            "-user_agent", "Mozilla/5.0", "-i", audio_url,
            "-map", "0:v:0", "-map", "1:a:0",
            "-c", "copy", "-movflags", "+faststart",
            "-f", "mp4", temp,
        ]
        process = subprocess.run(args, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=1800)
        if process.returncode != 0:
            raise RuntimeError(process.stderr.decode("utf-8", "replace")[-400:])

        os.replace(temp, path)
        return path


def start_stream(video_id, height):
    """
    Запускает ffmpeg в fragmented MP4 и возвращает (process, tempfile, path).
    Фрагменты пишутся на диск, сервер отдаёт их по мере появления.
    """
    path = cache_path(video_id, height)
    if os.path.exists(path) and os.path.getsize(path) > 0:
        return None, None, path

    os.makedirs(CACHE_DIR, exist_ok=True)
    base, video, audio = find_tracks(video_id, height)
    video_url = resolve(base, video.get("url"))
    audio_url = resolve(base, audio.get("url"))
    temp = path + ".live"

    args = [
        FFMPEG, "-y", "-hide_banner", "-loglevel", "warning",
        "-user_agent", "Mozilla/5.0", "-i", video_url,
        "-user_agent", "Mozilla/5.0", "-i", audio_url,
        "-map", "0:v:0", "-map", "1:a:0",
        "-c", "copy",
        "-movflags", "frag_keyframe+empty_moov+default_base_moof",
        "-f", "mp4", temp,
    ]
    process = subprocess.Popen(args, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    return process, temp, path


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        print("[%s] %s" % (self.address_string(), fmt % args), flush=True)

    def serve_live(self, video_id, height):
        _serve_live(self, video_id, height)

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        live = re.match(r"^/live/([A-Za-z0-9_-]{11})$", parsed.path)
        if live:
            query = urllib.parse.parse_qs(parsed.query)
            try:
                height = int(query.get("q", ["720"])[0])
            except ValueError:
                height = 720
            self.serve_live(live.group(1), height)
            return

        match = re.match(r"^/mux/([A-Za-z0-9_-]{11})$", parsed.path)
        if not match:
            self.send_error(404, "Not found")
            return

        video_id = match.group(1)
        query = urllib.parse.parse_qs(parsed.query)
        try:
            height = int(query.get("q", ["720"])[0])
        except ValueError:
            height = 720

        try:
            path = mux(video_id, height)
        except Exception as exc:
            self.send_response(502)
            self.send_header("Content-Type", "text/plain; charset=utf-8")
            body = str(exc).encode("utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return

        size = os.path.getsize(path)
        range_header = self.headers.get("Range")
        start = 0
        end = size - 1
        status = 200

        if range_header:
            range_match = re.match(r"bytes=(\d*)-(\d*)", range_header)
            if range_match:
                if range_match.group(1):
                    start = int(range_match.group(1))
                if range_match.group(2):
                    end = min(int(range_match.group(2)), size - 1)
                status = 206

        length = end - start + 1
        with open(path, "rb") as file:
            file.seek(start)
            self.send_response(status)
            self.send_header("Content-Type", "video/mp4")
            self.send_header("Accept-Ranges", "bytes")
            self.send_header("Content-Length", str(length))
            if status == 206:
                self.send_header("Content-Range", "bytes %d-%d/%d" % (start, end, size))
            self.end_headers()
            remaining = length
            while remaining > 0:
                chunk = file.read(min(262144, remaining))
                if not chunk:
                    break
                self.wfile.write(chunk)
                remaining -= len(chunk)


def _serve_file(handler, path):
    size = os.path.getsize(path)
    range_header = handler.headers.get("Range")
    start = 0
    end = size - 1
    status = 200

    if range_header:
        range_match = re.match(r"bytes=(\d*)-(\d*)", range_header)
        if range_match:
            if range_match.group(1):
                start = int(range_match.group(1))
            if range_match.group(2):
                end = min(int(range_match.group(2)), size - 1)
            status = 206

    length = end - start + 1
    with open(path, "rb") as file:
        file.seek(start)
        handler.send_response(status)
        handler.send_header("Content-Type", "video/mp4")
        handler.send_header("Accept-Ranges", "bytes")
        handler.send_header("Content-Length", str(length))
        if status == 206:
            handler.send_header("Content-Range", "bytes %d-%d/%d" % (start, end, size))
        handler.end_headers()
        remaining = length
        while remaining > 0:
            chunk = file.read(min(262144, remaining))
            if not chunk:
                break
            handler.wfile.write(chunk)
            remaining -= len(chunk)


def _serve_live(handler, video_id, height):
    try:
        process, temp, path = start_stream(video_id, height)
    except Exception as exc:
        body = str(exc).encode("utf-8", "replace")
        handler.send_response(502)
        handler.send_header("Content-Type", "text/plain; charset=utf-8")
        handler.send_header("Content-Length", str(len(body)))
        handler.end_headers()
        handler.wfile.write(body)
        return

    if process is None:
        _serve_file(handler, path)
        return

    handler.send_response(200)
    handler.send_header("Content-Type", "video/mp4")
    handler.send_header("Transfer-Encoding", "chunked")
    handler.send_header("Cache-Control", "no-store")
    handler.end_headers()

    sent = 0
    try:
        while True:
            if os.path.exists(temp):
                size = os.path.getsize(temp)
                if size > sent:
                    with open(temp, "rb") as file:
                        file.seek(sent)
                        data = file.read(size - sent)
                    if data:
                        handler.wfile.write(("%x\r\n" % len(data)).encode() + data + b"\r\n")
                        handler.wfile.flush()
                        sent += len(data)
            if process.poll() is not None:
                if os.path.exists(temp) and os.path.getsize(temp) > sent:
                    continue
                break
            time.sleep(0.2)

        handler.wfile.write(b"0\r\n\r\n")
        handler.wfile.flush()

        if process.returncode == 0 and os.path.exists(temp):
            os.replace(temp, path)
    except (BrokenPipeError, ConnectionResetError):
        process.kill()
    finally:
        if process.poll() is None:
            process.kill()


def main():
    global FFMPEG
    parser = argparse.ArgumentParser()
    parser.add_argument("--ffmpeg", default="ffmpeg")
    parser.add_argument("--port", type=int, default=8765)
    args = parser.parse_args()

    FFMPEG = args.ffmpeg
    os.makedirs(CACHE_DIR, exist_ok=True)
    print("CrimTube mux server on http://0.0.0.0:%d (ffmpeg=%s)" % (args.port, FFMPEG), flush=True)
    ThreadingHTTPServer(("0.0.0.0", args.port), Handler).serve_forever()


if __name__ == "__main__":
    main()
