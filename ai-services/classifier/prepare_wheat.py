"""Download the Zenodo wheat disease dataset (record 7307816) into data/multi/Wheat___<Label>/.

The record exposes ~999 individual image files. Each filename starts with its class:
YellowRust*, BrownRust*, Septoria*, Mildew*, Healthy*. No login is required.
"""
import os
import re
from concurrent.futures import ThreadPoolExecutor

import requests

RECORD = "https://zenodo.org/api/records/7307816"
OUT = "ai-services/classifier/data/multi"

LABELS = {
    "yellowrust": "Yellow_Rust",
    "brownrust": "Brown_Rust",
    "septoria": "Septoria",
    "mildew": "Mildew",
    "healthy": "Healthy",
}
IMAGE_SUFFIXES = (".png", ".jpg", ".jpeg")


def label_for(filename):
    stem = os.path.splitext(filename)[0]
    letters = re.match(r"[A-Za-z]+", stem)
    if not letters:
        return None
    return LABELS.get(letters.group(0).lower())


def download(session, url, path):
    response = session.get(url, timeout=120)
    response.raise_for_status()
    with open(path, "wb") as handle:
        handle.write(response.content)


def main():
    record = requests.get(RECORD, timeout=60).json()
    files = [f["key"] for f in record["files"] if f["key"].lower().endswith(IMAGE_SUFFIXES)]
    print(f"files listed: {len(files)}", flush=True)

    counts = {}
    jobs = []
    session = requests.Session()
    for name in files:
        label = label_for(name)
        if label is None:
            continue
        folder = os.path.join(OUT, f"Wheat___{label}")
        os.makedirs(folder, exist_ok=True)
        index = counts.get(folder, 0)
        counts[folder] = index + 1
        path = os.path.join(folder, f"zen{index:05d}{os.path.splitext(name)[1].lower()}")
        url = f"https://zenodo.org/api/records/7307816/files/{name}/content"
        jobs.append((url, path))

    with ThreadPoolExecutor(max_workers=12) as pool:
        list(pool.map(lambda job: download(session, *job), jobs))

    print("downloaded:", flush=True)
    for folder, n in sorted(counts.items()):
        print(f"  {os.path.basename(folder):<28}{n:>5}", flush=True)


if __name__ == "__main__":
    main()
