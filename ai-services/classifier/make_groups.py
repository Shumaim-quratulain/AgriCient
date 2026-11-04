import csv

import numpy as np
import torch
from PIL import Image
from scipy.sparse import csr_matrix
from scipy.sparse.csgraph import connected_components
from torch.utils.data import DataLoader, Dataset
from torchvision.models import mobilenet_v3_small

from dataset import CLASSES, files_for, make_transform

THRESHOLD = 0.90
OUT = "ai-services/classifier/data/groups.csv"


class Photos(Dataset):
    def __init__(self, paths):
        self.paths = paths
        self.transform = make_transform(train=False)

    def __len__(self):
        return len(self.paths)

    def __getitem__(self, i):
        return self.transform(Image.open(self.paths[i]).convert("RGB"))


def embed(model, device, paths):
    parts = []
    with torch.no_grad():
        # num_workers=0: on macOS extra loader processes re-import torch and cost
        # more than they save here (measured 279 img/s with 0, 53 img/s with 4)
        for batch in DataLoader(Photos(paths), batch_size=64, num_workers=0):
            pooled = model.avgpool(model.features(batch.to(device))).flatten(1)
            parts.append(pooled.cpu().numpy())
    return np.concatenate(parts)


def main():
    device = torch.device("mps" if torch.backends.mps.is_available() else "cpu")
    model = mobilenet_v3_small()
    model.load_state_dict(torch.load("ai-services/models/mobilenet_v3_small.pth", map_location="cpu"))
    model.eval().to(device)

    rows = []
    for name in CLASSES:
        paths = files_for(name)
        feats = embed(model, device, paths)
        feats = feats - feats.mean(axis=0)
        feats = feats / (np.linalg.norm(feats, axis=1, keepdims=True) + 1e-8)
        similar = (feats @ feats.T) >= THRESHOLD
        count, labels = connected_components(csr_matrix(similar), directed=False)
        sizes = np.bincount(labels)
        print(f"{name:<45}{len(paths):>6} images {count:>6} groups  largest {sizes.max()}", flush=True)
        rows += [(path, f"{name}#{g}") for path, g in zip(paths, labels)]

    with open(OUT, "w", newline="") as f:
        csv.writer(f).writerows(rows)


if __name__ == "__main__":
    main()