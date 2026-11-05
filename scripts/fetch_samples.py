#!/usr/bin/env python3
"""Download freely licensed restaurant-menu photos from Wikimedia Commons for the Phase 0 test.

Images go to samples/images/ (gitignored); licence/author/source metadata goes to
samples/manifest.json, which is committed. Only files under CC0 / CC BY / CC BY-SA / public domain
are kept.

Usage: python3 scripts/fetch_samples.py [--per-query 8]
Needs network access to commons.wikimedia.org and upload.wikimedia.org.
"""
import argparse, json, pathlib, re, time, urllib.error, urllib.parse, urllib.request

API = "https://commons.wikimedia.org/w/api.php"
UA = "menu-reader-samples/0.1 (https://github.com/pomali/menu-reader)"
QUERIES = [
    "jedálny lístok", "denné menu", "reštaurácia menu Bratislava", "menu Slovakia restaurant",
    "jídelní lístek", "denní menu", "restaurace menu Praha", "menu Czech restaurant",
    "alergeny menu", "restaurant menu board", "pub menu chalkboard", "restaurant menu prices",
]
OK_LICENSE = re.compile(r"^(cc0|cc[- ]by(-sa)?[- ]?\d|public domain|pd)", re.I)
ROOT = pathlib.Path(__file__).resolve().parent.parent / "samples"


def fetch(url, timeout=60, tries=8):
    """GET with retries. Wikimedia answers 429 with Retry-After on shared IPs."""
    for i in range(tries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=timeout) as r:
                return r.read()
        except urllib.error.HTTPError as e:
            if e.code not in (429, 503) or i == tries - 1:
                raise
            wait = float(e.headers.get("Retry-After") or 0) or 2 ** i
            time.sleep(min(wait + 1, 60))


def get(params):
    return json.loads(fetch(API + "?" + urllib.parse.urlencode({**params, "format": "json"}), 30))


def search(q, n):
    data = get({
        "action": "query", "generator": "search", "gsrsearch": f"{q} filetype:bitmap",
        "gsrnamespace": 6, "gsrlimit": n, "prop": "imageinfo",
        "iiprop": "url|extmetadata|size|mime", "iiurlwidth": 1920,  # must be a standard thumbnail step (https://w.wiki/GHai)
    })
    return (data.get("query") or {}).get("pages", {}).values()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--per-query", type=int, default=8)
    a = ap.parse_args()
    (ROOT / "images").mkdir(parents=True, exist_ok=True)
    manifest_path = ROOT / "manifest.json"
    manifest = json.loads(manifest_path.read_text()) if manifest_path.exists() else {}
    for q in QUERIES:
        for page in search(q, a.per_query):
            title = page["title"]
            if title in manifest or "imageinfo" not in page:
                continue
            ii = page["imageinfo"][0]
            meta = {k: v.get("value", "") for k, v in ii.get("extmetadata", {}).items()}
            lic = re.sub("<[^>]+>", "", meta.get("LicenseShortName", ""))
            if not OK_LICENSE.match(lic) or ii.get("mime") not in ("image/jpeg", "image/png"):
                continue
            src = ii.get("thumburl") or ii["url"]
            name = re.sub(r"[^\w.-]+", "_", title.removeprefix("File:"))[:120]
            try:
                (ROOT / "images" / name).write_bytes(fetch(src))
            except urllib.error.HTTPError as e:
                print(f"! {name}: {e}")
                continue
            manifest[title] = {
                "file": f"images/{name}", "query": q, "license": lic,
                "author": re.sub("<[^>]+>", "", meta.get("Artist", "")).strip(),
                "source": ii.get("descriptionurl"),
            }
            print(f"+ {name}  [{lic}]")
            manifest_path.write_text(json.dumps(manifest, indent=2, ensure_ascii=False))
            time.sleep(2)
        time.sleep(2)
    manifest_path.write_text(json.dumps(manifest, indent=2, ensure_ascii=False))
    print(f"{len(manifest)} images in manifest. Review them by hand and delete the ones that aren't menus.")


if __name__ == "__main__":
    main()
