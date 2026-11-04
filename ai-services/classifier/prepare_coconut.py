"""Download the FULL coconut dataset (no per-class cap) into data/multi/Coconut___<Condition>/.

Same rules as prepare_multi.py: only the shortest side kept at 256 px, the Hugging Face
cache redirected into data/hf-hub and wiped afterwards. Images are written to a
temporary folder first and swapped in only when the download finished, so a failure
leaves the existing photos untouched.
"""
import os
import shutil

HERE = "ai-services/classifier/data"
OUT = os.path.join(HERE, "multi")
TMP = os.path.join(HERE, "multi_coconut_new")
HUB = os.path.abspath(os.path.join(HERE, "hf-hub"))
CACHE = os.path.abspath(os.path.join(HERE, "hf-cache"))
REPO = "Project-AgML/coconut_tree_disease_classification"
IMG_SIDE = 256

os.environ["HF_HUB_CACHE"] = HUB   # must be set before datasets is imported

from datasets import load_dataset
from PIL import Image


def clean(text):
    return "_".join(str(text).strip().split())


def save_image(picture, path):
    rgb = picture.convert("RGB")
    rgb.thumbnail((IMG_SIDE, IMG_SIDE), Image.LANCZOS)
    rgb.save(path, quality=92)


def main():
    shutil.rmtree(TMP, ignore_errors=True)
    os.makedirs(TMP)
    os.makedirs(CACHE, exist_ok=True)
    os.makedirs(HUB, exist_ok=True)
    try:
        ds = load_dataset(REPO, split="train", cache_dir=CACHE)
        names = ds.features["label"].names
        counts = {}
        for i, example in enumerate(ds):
            label = clean(names[example["label"]])
            folder = os.path.join(TMP, f"Coconut___{label}")
            os.makedirs(folder, exist_ok=True)
            n = counts.get(label, 0)
            save_image(example["image"], os.path.join(folder, f"{n:05d}.jpg"))
            counts[label] = n + 1
            if i % 500 == 0:
                print(f"  {i} / {len(ds)}", flush=True)
    finally:
        shutil.rmtree(HUB, ignore_errors=True)
        shutil.rmtree(CACHE, ignore_errors=True)
        os.makedirs(HUB, exist_ok=True)
        os.makedirs(CACHE, exist_ok=True)

    print("downloaded:", counts, "total", sum(counts.values()))
    # swap in only after a complete download
    for label in counts:
        target = os.path.join(OUT, f"Coconut___{label}")
        shutil.rmtree(target, ignore_errors=True)
        shutil.move(os.path.join(TMP, f"Coconut___{label}"), target)
    shutil.rmtree(TMP, ignore_errors=True)
    print("done")


if __name__ == "__main__":
    main()
