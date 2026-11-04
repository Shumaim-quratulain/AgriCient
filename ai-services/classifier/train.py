import sys

import torch
from torch import nn
from torch.utils.data import DataLoader
from torchvision.models import mobilenet_v3_small

from dataset import CLASSES, PlantVillage
device = torch.device("mps" if torch.backends.mps.is_available() else "cpu")

model = mobilenet_v3_small()
model.load_state_dict(torch.load("ai-services/models/mobilenet_v3_small.pth", map_location="cpu"))

for parameter in model.parameters():
    parameter.requires_grad = False

old_layer = model.classifier[3]
model.classifier[3] = nn.Linear(old_layer.in_features, len(CLASSES))
model = model.to(device)

print("device:", device)
print("trainable parameters:", sum(p.numel() for p in model.parameters() if p.requires_grad))

EPOCHS = 3
BATCH_SIZE = 64
LEARNING_RATE = 0.001
MAX_BATCHES = int(sys.argv[1]) if len(sys.argv) > 1 else None

train_loader = DataLoader(PlantVillage("train", train=True), batch_size=BATCH_SIZE, shuffle=True)
test_loader = DataLoader(PlantVillage("test", train=False), batch_size=BATCH_SIZE)

loss_function = nn.CrossEntropyLoss()
optimizer = torch.optim.Adam(model.classifier[3].parameters(), lr=LEARNING_RATE)


def accuracy(loader):
    model.eval()
    correct = 0
    total = 0
    with torch.no_grad():
        for batch, (images, labels) in enumerate(loader):
            if MAX_BATCHES and batch >= MAX_BATCHES:
                break
            images, labels = images.to(device), labels.to(device)
            predicted = model(images).argmax(dim=1)
            correct += (predicted == labels).sum().item()
            total += labels.size(0)
    return correct / total


for epoch in range(EPOCHS):
    model.train()
    model.features.eval()
    for batch, (images, labels) in enumerate(train_loader):
        if MAX_BATCHES and batch >= MAX_BATCHES:
            break
        images, labels = images.to(device), labels.to(device)

        loss = loss_function(model(images), labels)
        optimizer.zero_grad()
        loss.backward()
        optimizer.step()

        if batch % 50 == 0:
            print(f"epoch {epoch + 1}/{EPOCHS}  batch {batch}  loss {loss.item():.3f}", flush=True)

    print(f"epoch {epoch + 1} done: test accuracy {accuracy(test_loader):.3f}", flush=True)

if MAX_BATCHES is None:
    torch.save(model.state_dict(), "ai-services/models/plantvillage_mobilenet_v3_small.pth")
    with open("ai-services/models/plantvillage_classes.txt", "w") as f:
        f.write("\n".join(CLASSES))
    print("saved model and class list")