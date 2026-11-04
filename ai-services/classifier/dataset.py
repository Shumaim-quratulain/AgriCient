import os
import random
import csv
from PIL import Image
from torch.utils.data import Dataset
from torchvision import transforms

ROOTS = [
    "ai-services/classifier/data/multi",
]
IMAGE_SUFFIXES = (".jpg", ".jpeg", ".png")
VAL_FRACTION = 0.2
SEED = 42
GROUPS_CSV = "ai-services/classifier/data/groups.csv"

# Several sources name the same disease differently. Left alone, the model is
# asked to tell "Blast" from "Leaf_Blast" from "Rice_Blast", which is impossible.
CANONICAL = {
    "Rice___Leaf_Blast": "Rice___Blast",
    "Rice___Rice_Blast": "Rice___Blast",
    "Rice___Healthy_Rice_Leaf": "Rice___Healthy",
    "Rice___normal": "Rice___Healthy",
    "Rice___brown_spot": "Rice___Brownspot",
    "Rice___bacterial_leaf_blight": "Rice___Bacterialblight",
    "Rice___Rice_Tungro": "Rice___Tungro",
}
# A class needs enough examples to learn; below this it only adds noise.
MIN_IMAGES = 30
MAX_PER_CLASS = 10000
MEAN = [0.485, 0.456, 0.406]
STD = [0.229, 0.224, 0.225]


def make_transform(train):
    steps = [transforms.Resize(256), transforms.CenterCrop(224)]
    if train:
        steps.append(transforms.RandomHorizontalFlip())
    steps += [transforms.ToTensor(), transforms.Normalize(MEAN, STD)]
    return transforms.Compose(steps)


def canonical(name):
    """Folder name -> the single class name it belongs to."""
    if name.startswith("NotCoconut___"):
        return "NotCoconut"          # every source folder is the same class
    return CANONICAL.get(name, name)


def raw_folders():
    """canonical class name -> every folder, in any root, that feeds it."""
    folders = {}
    for root in ROOTS:
        if not os.path.isdir(root):
            continue
        for name in sorted(os.listdir(root)):
            path = os.path.join(root, name)
            if os.path.isdir(path):
                folders.setdefault(canonical(name), []).append(path)
    return folders


FOLDERS = raw_folders()


def files_for(class_name):
    paths = []
    for folder in FOLDERS[class_name]:
        for name in sorted(os.listdir(folder)):
            if name.lower().endswith(IMAGE_SUFFIXES):
                paths.append(os.path.join(folder, name))
    if len(paths) > MAX_PER_CLASS:
        random.Random(SEED).shuffle(paths)
        paths = sorted(paths[:MAX_PER_CLASS])
    return paths

def scan_classes():
    """Canonical classes with at least MIN_IMAGES images."""
    return sorted(name for name in FOLDERS if len(files_for(name)) >= MIN_IMAGES)


CLASSES = scan_classes()
CLASS_TO_INDEX = {name: i for i, name in enumerate(CLASSES)}


def load_groups():
    with open(GROUPS_CSV) as f:
        return {path: group for path, group in csv.reader(f)}


def build_split():
    groups = load_groups()
    train, test = [], []
    for class_name in CLASSES:
        paths = files_for(class_name)
        by_group = {}
        for path in paths:
            by_group.setdefault(groups[path], []).append(path)
        names = sorted(by_group)
        random.Random(SEED + CLASS_TO_INDEX[class_name]).shuffle(names)
        goal = max(1, int(len(paths) * VAL_FRACTION))
        taken = 0
        for name in names:
            if taken < goal:
                test += by_group[name]
                taken += len(by_group[name])
            else:
                train += by_group[name]
    return {"train": train, "test": test}


_SPLIT = None


class PlantVillage(Dataset):
    def __init__(self, split, train):
        global _SPLIT
        if _SPLIT is None:
            _SPLIT = build_split()
        self.paths = _SPLIT[split]
        self.transform = make_transform(train)

    def __len__(self):
        return len(self.paths)

    def __getitem__(self, i):
        path = self.paths[i]
        image = Image.open(path).convert("RGB")
        label = CLASS_TO_INDEX[canonical(os.path.basename(os.path.dirname(path)))]
        return self.transform(image), label