"""Озвучка видео: авто-субтитры -> перевод -> TTS -> дорожка -> MP4."""

import html
import json
import os
import re
import subprocess
import threading
import time
import urllib.parse
import urllib.request

INSTANCES = [
    "http://5.253.247.182:3000",
    "http://82.69.47.60:31008",
    "http://41.193.14.3:3000",
    "http://10.0.0.1:3000",
]

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
CACHE_DIR = os.path.join(BASE_DIR, "cache", "dub")
FFMPEG = os.environ.get("FFMPEG", "ffmpeg")
FFPROBE = os.environ.get("FFPROBE", "ffprobe")
JOBS = {}
LOCK = threading.Lock()

INSTANCES = [
    "http://5.253.247.182:3000",
    "http://82.69.47.60:31008",
    "http://41.193.14.3:3000",
    "http://73.26.148.72:3000",
    "http://14.225.212.141:3000",
]

PIPED_INSTANCES = [
    "http://124.158.10.214:8081",
    "http://pipedapi.xorrito.info",
    "http://pipedapi.hugoblomdahl.se",
    "http://pipedapi.r4vermind.org",
]


def http_json(url, timeout=25):
    request = urllib.request.Request(url, headers={"User-Agent": "CrimTube-dub/1.0"})
    with urllib.request.urlopen(request, timeout=timeout) as response:
        return json.loads(response.read().decode("utf-8", "replace"))


def http_text(url, timeout=25):
    request = urllib.request.Request(url, headers={"User-Agent": "CrimTube-dub/1.0"})
    with urllib.request.urlopen(request, timeout=timeout) as response:
        return response.read().decode("utf-8", "replace")


def http_bytes(url, timeout=60):
    request = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(request, timeout=timeout) as response:
        return response.read()


def resolve(base, url):
    if url.startswith("http://") or url.startswith("https://"):
        return url
    return base.rstrip("/") + (url if url.startswith("/") else "/" + url)


def pick_stream(details):
    streams = details.get("formatStreams") or []
    best = None
    for stream in streams:
        if not stream.get("url"):
            continue
        label = stream.get("qualityLabel") or ""
        match = re.match(r"(\d+)", label)
        height = int(match.group(1)) if match else 0
        if best is None or height > best[0]:
            best = (height, stream)
    return best[1] if best else None


def pick_caption(captions):
    if not captions:
        return None
    english = [c for c in captions if "english" in (c.get("label") or "").lower()]
    if not english:
        return captions[0]
    manual = [c for c in english if "auto" not in (c.get("label") or "").lower()]
    return (manual or english)[0]


def parse_timestamp(value):
    parts = value.strip().replace(",", ".").split(":")
    if len(parts) == 3:
        hours, minutes, seconds = parts
    else:
        hours, minutes, seconds = "0", parts[0], parts[1]
    return int(hours) * 3600 + int(minutes) * 60 + float(seconds)


def parse_ttml(text):
    import xml.etree.ElementTree as ET
    cues = []
    try:
        root = ET.fromstring(text)
    except Exception:
        return cues
    for element in root.iter():
        if not element.tag.endswith("p"):
            continue
        begin = element.attrib.get("begin")
        end = element.attrib.get("end")
        if not begin or not end:
            continue
        raw = "".join(element.itertext())
        raw = re.sub(r"\s+", " ", raw).strip()
        if raw:
            cues.append({"start": parse_timestamp(begin), "end": parse_timestamp(end), "text": raw})
    return cues


def piped_captions(video_id):
    for base in PIPED_INSTANCES:
        try:
            data = http_json(base.rstrip("/") + "/streams/" + urllib.parse.quote(video_id), timeout=25)
            subtitles = data.get("subtitles") or []
            if not subtitles:
                continue
            english = [s for s in subtitles if (s.get("code") or "").lower().startswith("en")
                       or "english" in (s.get("name") or "").lower()]
            chosen = (english or subtitles)[0]
            url = chosen.get("url")
            if not url:
                continue
            text = http_text(url, timeout=30)
            cues = parse_ttml(text)
            if cues:
                return cues
        except Exception:
            continue
    return []


def parse_vtt(text):
    cues = []
    lines = text.splitlines()
    index = 0
    while index < len(lines):
        line = lines[index].strip()
        if "-->" not in line:
            index += 1
            continue
        try:
            start_raw, end_raw = line.split("-->")[:2]
            start = parse_timestamp(start_raw.split()[0])
            end = parse_timestamp(end_raw.split()[0])
        except Exception:
            index += 1
            continue
        index += 1
        parts = []
        while index < len(lines) and lines[index].strip():
            parts.append(lines[index].strip())
            index += 1
        raw = " ".join(parts)
        raw = re.sub(r"<[^>]+>", "", raw)
        raw = html.unescape(raw)
        raw = raw.replace("&nbsp;", " ").strip()
        if raw and not re.match(r"^\[.*\]$", raw):
            cues.append({"start": start, "end": end, "text": raw})
    return cues


def merge_cues(cues):
    segments = []
    for cue in cues:
        if segments:
            last = segments[-1]
            gap = cue["start"] - last["end"]
            if gap < 1.0 and len(last["text"]) + len(cue["text"]) < 140:
                last["end"] = cue["end"]
                last["text"] = last["text"] + " " + cue["text"]
                continue
        segments.append({"start": cue["start"], "end": cue["end"], "text": cue["text"]})
    return segments


def translate(text):
    query = urllib.parse.quote(text)
    url = ("https://translate.googleapis.com/translate_a/single"
           "?client=gtx&sl=auto&tl=ru&dt=t&q=" + query)
    for _ in range(3):
        try:
            data = http_json(url)
            return "".join(part[0] for part in data[0] if part and part[0]).strip()
        except Exception:
            time.sleep(1)
    return ""


def tts(text, output_path):
    query = urllib.parse.quote(text)
    url = ("https://translate.google.com/translate_tts"
           "?ie=UTF-8&client=tw-ob&tl=ru&q=" + query)
    for _ in range(3):
        try:
            data = http_bytes(url)
            if data and data[:2] in (b"\xff\xfb", b"\xff\xf3", b"\xff\xf2"):
                with open(output_path, "wb") as file:
                    file.write(data)
                return True
        except Exception:
            time.sleep(1)
    return False


def probe_duration(path):
    try:
        output = subprocess.check_output([
            FFPROBE, "-v", "error", "-show_entries", "format=duration",
            "-of", "default=nw=1:nk=1", path
        ], stderr=subprocess.DEVNULL, timeout=30)
        return float(output.decode("utf-8", "replace").strip())
    except Exception:
        try:
            process = subprocess.run([FFMPEG, "-i", path], stdout=subprocess.PIPE,
                                     stderr=subprocess.STDOUT, timeout=30)
            text = process.stdout.decode("utf-8", "replace")
            match = re.search(r"Duration: (\d+):(\d+):(\d+\.\d+)", text)
            if match:
                return int(match.group(1)) * 3600 + int(match.group(2)) * 60 + float(match.group(3))
        except Exception:
            pass
        return 0.0


def find_source(video_id):
    last_error = None
    for base in INSTANCES:
        try:
            details = http_json(base + "/api/v1/videos/" + urllib.parse.quote(video_id) + "?local=true")
            stream = pick_stream(details)
            if not stream:
                continue
            captions = http_json(base + "/api/v1/captions/" + urllib.parse.quote(video_id))
            caption = pick_caption(captions.get("captions") or [])
            if not caption:
                raise RuntimeError("у видео нет субтитров для озвучки")
            caption_url = resolve(base, caption.get("url") or "")
            vtt = http_text(caption_url)
            cues = parse_vtt(vtt)
            if not cues:
                cues = piped_captions(video_id)
            if not cues:
                raise RuntimeError("субтитры пустые")
            return base, resolve(base, stream["url"]), cues
        except Exception as exc:
            last_error = exc
    raise RuntimeError(str(last_error) if last_error else "нет доступного инстанса")


def build_audio(segments, work_dir, report):
    inputs = []
    filters = []
    count = 0
    for index, segment in enumerate(segments):
        translated = translate(segment["text"])
        if not translated:
            continue
        count += 1
        audio_path = os.path.join(work_dir, "seg_%03d.mp3" % count)
        if not tts(translated, audio_path):
            continue
        duration = probe_duration(audio_path)
        target = max(0.6, segment["end"] - segment["start"])
        chain = "[%d:a]" % len(inputs)
        if duration > target * 1.05:
            tempo = min(2.0, duration / target)
            chain += "atempo=%.3f," % tempo
        delay = int(segment["start"] * 1000)
        chain += "adelay=%d|%d,volume=0.95[a%d]" % (delay, delay, len(inputs))
        filters.append(chain)
        inputs.append(audio_path)
        if count % 5 == 0:
            report(35 + int(45 * index / max(1, len(segments))), "озвучка %d/%d" % (index + 1, len(segments)))

    if not inputs:
        raise RuntimeError("не удалось озвучить ни один фрагмент")

    output = os.path.join(work_dir, "dub.m4a")
    command = [FFMPEG, "-y", "-hide_banner", "-loglevel", "error"]
    for path in inputs:
        command += ["-i", path]
    mix = "".join("[a%d]" % i for i in range(len(inputs)))
    command += [
        "-filter_complex", ";".join(filters) + ";" + mix + "amix=inputs=%d:normalize=0:duration=longest[aout]" % len(inputs),
        "-map", "[aout]", "-c:a", "aac", "-b:a", "128k", output,
    ]
    process = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=1800)
    if process.returncode != 0:
        raise RuntimeError(process.stderr.decode("utf-8", "replace")[-500:])
    return output


def mux(video_url, audio_path, output_path):
    command = [
        FFMPEG, "-y", "-hide_banner", "-loglevel", "error",
        "-user_agent", "Mozilla/5.0", "-i", video_url,
        "-i", audio_path,
        "-map", "0:v:0", "-map", "1:a:0",
        "-c:v", "copy", "-c:a", "aac", "-b:a", "128k",
        "-shortest", "-movflags", "+faststart",
        output_path,
    ]
    process = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=3600)
    if process.returncode != 0:
        raise RuntimeError(process.stderr.decode("utf-8", "replace")[-500:])


def job_key(video_id, lang):
    return "%s_%s" % (video_id, lang)


def status(video_id, lang):
    key = job_key(video_id, lang)
    path = os.path.join(CACHE_DIR, key + ".mp4")
    if os.path.exists(path) and os.path.getsize(path) > 0:
        return {"status": "ready", "progress": 100, "message": "готово"}
    return JOBS.get(key, {"status": "idle", "progress": 0, "message": "не начато"})


def prepare(video_id, lang="ru"):
    os.makedirs(CACHE_DIR, exist_ok=True)
    key = job_key(video_id, lang)
    path = os.path.join(CACHE_DIR, key + ".mp4")
    if os.path.exists(path) and os.path.getsize(path) > 0:
        return status(video_id, lang)
    with LOCK:
        if key in JOBS and JOBS[key].get("status") == "working":
            return JOBS[key]
        JOBS[key] = {"status": "working", "progress": 1, "message": "получаю видео"}
    threading.Thread(target=run_job, args=(video_id, lang), daemon=True).start()
    return JOBS[key]


def run_job(video_id, lang):
    key = job_key(video_id, lang)
    path = os.path.join(CACHE_DIR, key + ".mp4")
    work_dir = os.path.join(CACHE_DIR, key + "_work")
    os.makedirs(work_dir, exist_ok=True)

    def report(progress, message):
        JOBS[key] = {"status": "working", "progress": progress, "message": message}

    try:
        report(5, "ищу субтитры")
        base, video_url, cues = find_source(video_id)
        segments = merge_cues(cues)
        report(15, "субтитры найдены: %d фрагментов" % len(segments))
        audio_path = build_audio(segments, work_dir, report)
        report(85, "склеиваю видео и озвучку")
        temp_output = os.path.join(work_dir, "result.mp4")
        mux(video_url, audio_path, temp_output)
        os.replace(temp_output, path)
        JOBS[key] = {"status": "ready", "progress": 100, "message": "готово"}
    except Exception as exc:
        JOBS[key] = {"status": "error", "progress": 0, "message": "ошибка", "error": str(exc)[-500:]}
