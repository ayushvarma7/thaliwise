#!/usr/bin/env python3
"""Download a small labeled photo subset for the reference memory.

For every class in classes.tsv, fetch N_REF photos from the dataset's train split (the reference memory)
and N_TEST photos from its held-out split (validation or test, never used as references), through the
Hugging Face datasets-server API, one photo at a time. Each photo is resized like the app does before
inference (longest side at most 512 px, JPEG quality 90) and written under OUT_DIR, which is inside the
gitignored .toolchain folder: the photos are research or unlicensed data and must never be committed or
shipped. A manifest.tsv records where every photo came from.

Usage: python3 tools/refmem/download.py [--ref 14] [--test 6]
"""
import argparse
import io
import json
import os
import ssl
import sys
import time
import urllib.parse
import urllib.request

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
CLASSES = os.path.join(ROOT, "tools", "refmem", "classes.tsv")
OUT_DIR = os.path.join(ROOT, ".toolchain", "refmem", "images")
API = "https://datasets-server.huggingface.co"
HELD_OUT = {"ethz/food101": "validation", "rajistics/indian_food_images": "test"}
MAX_SIDE = 512          # Config.INFER_IMAGE_MAX_DIM
JPEG_QUALITY = 90       # ImageUtil.prepareForInference
# The python.org macOS build ships without root certificates; use the system bundle instead of
# installing certificates globally.
TLS = ssl.create_default_context(cafile="/etc/ssl/cert.pem") if os.path.exists("/etc/ssl/cert.pem") else None


def get_json(url, tries=8):
    for attempt in range(tries):
        try:
            with urllib.request.urlopen(url, timeout=60, context=TLS) as r:
                data = json.load(r)
        except Exception as e:  # network hiccup: retry with backoff
            data = {"error": str(e)}
        if "error" not in data:
            return data
        wait = 20 if "loading" in data["error"] else 2 ** attempt
        print("  api: %s, retry in %ds" % (data["error"][:80], wait), flush=True)
        time.sleep(wait)
    raise RuntimeError("API failed: " + url)


def dataset_info(dataset):
    info = get_json(API + "/info?" + urllib.parse.urlencode({"dataset": dataset}))["dataset_info"]["default"]
    names = [f for f in info["features"].values() if f.get("_type") == "ClassLabel"][0]["names"]
    sizes = {k: v["num_examples"] for k, v in info["splits"].items()}
    return names, sizes


def get_rows(dataset, split, offset, length):
    q = urllib.parse.urlencode({"dataset": dataset, "config": "default", "split": split,
                                "offset": offset, "length": length})
    return get_json(API + "/rows?" + q)["rows"]


def rows_by_label(dataset, split, size, n_classes, wanted, n):
    """The first n rows of each wanted label. The /filter endpoint needs a server-side index that can take
    a long time to build, so this uses /rows only. Food-101 stores each class as one contiguous block
    (750 train or 250 validation rows, in no particular class order): probe the first row of every block,
    then read n rows from the right block. Other datasets are scanned page by page. Every returned row's
    label is checked, so a block that is not uniform cannot add wrong photos."""
    out = {label: [] for label in wanted}
    if size % n_classes == 0:
        block = size // n_classes
        start = {}
        for k in range(n_classes):
            label = get_rows(dataset, split, k * block, 1)[0]["row"]["label"]
            start.setdefault(label, k * block)
        for label in wanted:
            rows = get_rows(dataset, split, start[label], n)
            out[label] = [r for r in rows if r["row"]["label"] == label][:n]
        return out
    offset = 0
    while offset < size and any(len(v) < n for v in out.values()):
        for r in get_rows(dataset, split, offset, 100):
            label = r["row"]["label"]
            if label in out and len(out[label]) < n:
                out[label].append(r)
        offset += 100
    return out


def fetch_image(dataset, split, row):
    """The photo bytes. Image links are signed and can expire, so a failed link is fetched again once."""
    try:
        with urllib.request.urlopen(row["row"]["image"]["src"], timeout=60, context=TLS) as resp:
            return resp.read()
    except Exception:
        fresh = get_rows(dataset, split, row["row_idx"], 1)[0]
        with urllib.request.urlopen(fresh["row"]["image"]["src"], timeout=60, context=TLS) as resp:
            return resp.read()


def save_like_app(raw, path):
    img = Image.open(io.BytesIO(raw)).convert("RGB")
    w, h = img.size
    if max(w, h) > MAX_SIDE:
        s = MAX_SIDE / float(max(w, h))
        img = img.resize((max(1, round(w * s)), max(1, round(h * s))), Image.BILINEAR)
    img.save(path, "JPEG", quality=JPEG_QUALITY)
    return img.size


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--ref", type=int, default=14)
    ap.add_argument("--test", type=int, default=6)
    args = ap.parse_args()

    entries = []
    with open(CLASSES) as f:
        for line in f:
            if line.startswith("#") or not line.strip():
                continue
            source, dataset, cls, food_id = line.rstrip("\n").split("\t")
            entries.append((source, dataset, cls, food_id))

    infos = {d: dataset_info(d) for d in sorted({e[1] for e in entries})}
    plan = {}   # (dataset, split) -> label -> rows
    for dataset, (names, sizes) in infos.items():
        wanted = [names.index(e[2]) for e in entries if e[1] == dataset]
        for split, n in (("train", args.ref), (HELD_OUT[dataset], args.test)):
            print("indexing %s %s (%d rows)" % (dataset, split, sizes[split]), flush=True)
            plan[(dataset, split)] = rows_by_label(dataset, split, sizes[split], len(names), wanted, n)
    manifest = os.path.join(os.path.dirname(OUT_DIR), "manifest.tsv")
    os.makedirs(OUT_DIR, exist_ok=True)
    total_bytes = 0
    count = 0
    with open(manifest, "w") as out:
        out.write("role\tfood_id\tsource\tdataset\tsplit\tclass\trow_idx\tpath\twidth\theight\n")
        for source, dataset, cls, food_id in entries:
            label = infos[dataset][0].index(cls)
            for role, split in (("ref", "train"), ("test", HELD_OUT[dataset])):
                rows = plan[(dataset, split)][label]
                d = os.path.join(OUT_DIR, role, food_id)
                os.makedirs(d, exist_ok=True)
                for r in rows:
                    path = os.path.join(d, "%s_%s_%s_%d.jpg" % (source, cls, split, r["row_idx"]))
                    if not os.path.exists(path):
                        raw = fetch_image(dataset, split, r)
                        total_bytes += len(raw)
                        w, h = save_like_app(raw, path)
                    else:
                        w, h = Image.open(path).size
                    count += 1
                    out.write("\t".join(map(str, (role, food_id, source, dataset, split, cls, r["row_idx"],
                                                   os.path.relpath(path, ROOT), w, h))) + "\n")
                print("%-6s %-4s %-26s -> %-22s %d photos" % (source, role, cls, food_id, len(rows)), flush=True)
                time.sleep(0.2)
    print("photos: %d, downloaded: %.1f MB, manifest: %s" % (count, total_bytes / 1e6,
                                                             os.path.relpath(manifest, ROOT)))


if __name__ == "__main__":
    sys.exit(main())
