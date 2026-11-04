import torch
from torch import nn
from torch.utils.data import DataLoader
from torchvision.models import mobilenet_v3_small

from dataset import CLASSES, PlantVillage

device = torch.device("mps" if torch.backends.mps.is_available() else "cpu")
N = len(CLASSES)

model = mobilenet_v3_small()
model.classifier[3] = nn.Linear(model.classifier[3].in_features, N)
model.load_state_dict(torch.load("ai-services/models/plantvillage_mobilenet_v3_small.pth", map_location="cpu"))
model = model.to(device).eval()

loader = DataLoader(PlantVillage("test", train=False), batch_size=64)

# matrix[true class][predicted class] = how many photos fell there
matrix = torch.zeros(N, N, dtype=torch.long)

with torch.no_grad():
    for images, labels in loader:
        predicted = model(images.to(device)).argmax(dim=1).cpu()
        for true_class, guess in zip(labels.tolist(), predicted.tolist()):
            matrix[true_class][guess] += 1

correct = matrix.diag().sum().item()
total = matrix.sum().item()
print(f"test images: {total}")
print(f"accuracy:    {correct / total:.4f}")

f1_scores = []
print(f"\n{'class':<52}{'prec':>7}{'rec':>7}{'f1':>7}{'n':>6}")
for i, name in enumerate(CLASSES):
    tp = matrix[i][i].item()
    fp = matrix[:, i].sum().item() - tp
    fn = matrix[i, :].sum().item() - tp
    precision = tp / (tp + fp) if tp + fp else 0.0
    recall = tp / (tp + fn) if tp + fn else 0.0
    f1 = 2 * precision * recall / (precision + recall) if precision + recall else 0.0
    f1_scores.append(f1)
    print(f"{name:<52}{precision:>7.3f}{recall:>7.3f}{f1:>7.3f}{matrix[i].sum().item():>6}")

print(f"\nmacro-F1: {sum(f1_scores) / N:.4f}")

print("\nworst 5 classes by F1:")
for i in sorted(range(N), key=lambda j: f1_scores[j])[:5]:
    print(f"  {CLASSES[i]:<52}{f1_scores[i]:.3f}")

print("\nmost confused pairs (true -> what it answered):")
pairs = []
for t in range(N):
    for p in range(N):
        if t != p and matrix[t][p] > 0:
            pairs.append((matrix[t][p].item(), t, p))
for count, t, p in sorted(pairs, reverse=True)[:8]:
    print(f"  {count:>4}  {CLASSES[t]:<45} -> {CLASSES[p]}")

crop_of = [name.split("___")[0] for name in CLASSES]
crops = sorted(set(crop_of))

print(f"\n{'crop':<14}{'classes':>8}{'images':>8}{'accuracy':>10}")
crop_rows = []
for crop in crops:
    ids = [i for i in range(N) if crop_of[i] == crop]
    seen = sum(matrix[i].sum().item() for i in ids)
    right = sum(matrix[i][i].item() for i in ids)
    crop_rows.append((right / seen, crop, len(ids), seen))
for acc, crop, k, seen in sorted(crop_rows):
    print(f"{crop:<14}{k:>8}{seen:>8}{acc:>10.3f}")

coconut = [i for i in range(N) if crop_of[i] == "Coconut"]
print("\nCOCONUT confusion matrix (rows = true, columns = answered):")
print(" " * 22 + "".join(f"{CLASSES[j].split('___')[1][:9]:>11}" for j in coconut))
for i in coconut:
    print(f"{CLASSES[i].split('___')[1]:<22}" + "".join(f"{matrix[i][j].item():>11}" for j in coconut))
out_of_crop = sum(matrix[i].sum().item() - sum(matrix[i][j].item() for j in coconut) for i in coconut)
print(f"coconut photos answered as a non-coconut class: {out_of_crop}")