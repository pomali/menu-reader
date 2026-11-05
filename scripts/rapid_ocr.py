#!/usr/bin/env python3
"""Stand-in OCR for Phase 0 on a dev machine: RapidOCR (PaddleOCR models, ONNX) -> OcrDocument JSON.

ML Kit only runs on Android. This gives the parser realistic line boxes from real photos so it can be
tuned on the JVM before device dumps exist. Output goes to samples/ocr-rapid/<image>.json.
Text quality differs from ML Kit (diacritics especially), so treat scores as approximate.

Usage: pip install rapidocr_onnxruntime; python3 scripts/rapid_ocr.py samples/menu*.jpg
Optional: --rec-model path/to/latin_rec.onnx --rec-keys path/to/latin_dict.txt for a Latin model.
"""
import argparse, json, pathlib
from PIL import Image
from rapidocr_onnxruntime import RapidOCR

ap = argparse.ArgumentParser()
ap.add_argument("images", nargs="+")
ap.add_argument("--out", default=str(pathlib.Path(__file__).resolve().parent.parent / "samples" / "ocr-rapid"))
ap.add_argument("--rec-model")
ap.add_argument("--rec-keys")
a = ap.parse_args()
kw = {}
if a.rec_model:
    kw = {"rec_model_path": a.rec_model, "rec_keys_path": a.rec_keys}


def group_blocks(lines, page_w):
    """ML Kit returns blocks (paragraphs); RapidOCR returns lines. Stack left-aligned, close,
    similar-height lines into blocks so the parser sees the same structure as on the device."""
    blocks = []
    for ln in sorted(lines, key=lambda l: l["box"]["top"]):
        b, h = ln["box"], ln["box"]["bottom"] - ln["box"]["top"]
        for blk in reversed(blocks[-8:]):
            last = blk["lines"][-1]["box"]; lh = last["bottom"] - last["top"]
            if (abs(b["left"] - blk["box"]["left"]) < 0.015 * page_w and 0 <= b["top"] - last["bottom"] < 0.8 * max(h, lh)
                    and max(h, lh) < 1.5 * min(h, lh)):
                blk["lines"].append(ln)
                bb = blk["box"]
                blk["box"] = {"left": min(bb["left"], b["left"]), "top": bb["top"],
                              "right": max(bb["right"], b["right"]), "bottom": max(bb["bottom"], b["bottom"])}
                blk["text"] += "\n" + ln["text"]
                break
        else:
            blocks.append({"text": ln["text"], "box": dict(b), "lines": [ln]})
    return blocks


engine = RapidOCR(**kw) if a.images[0] != "--regroup" else None
out = pathlib.Path(a.out); out.mkdir(parents=True, exist_ok=True)
for path in a.images:
    p = pathlib.Path(path)
    w, h = Image.open(p).size
    result, _ = engine(str(p))
    lines = []
    for box, text, score in result or []:
        xs = [int(pt[0]) for pt in box]; ys = [int(pt[1]) for pt in box]
        bb = {"left": min(xs), "top": min(ys), "right": max(xs), "bottom": max(ys)}
        lines.append({"text": text, "box": bb, "confidence": round(float(score), 3)})
    blocks = group_blocks(lines, w)
    doc = {"pages": [{"pageId": "p1", "width": w, "height": h, "blocks": blocks}]}
    (out / f"{p.stem}.json").write_text(json.dumps(doc, ensure_ascii=False, indent=1))
    print(f"{p.name}: {len(blocks)} lines")
