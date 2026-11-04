import torch
from torch import nn
from torchvision.models import mobilenet_v3_small

from dataset import CLASSES

model = mobilenet_v3_small()
model.classifier[3] = nn.Linear(model.classifier[3].in_features, len(CLASSES))
model.load_state_dict(torch.load("ai-services/models/plantvillage_mobilenet_v3_small.pth", map_location="cpu"))
model.eval()

dummy = torch.rand(1, 3, 224, 224)
torch.onnx.export(
    model,
    dummy,
    "backend/src/main/resources/plantvillage_mobilenet_v3_small.onnx",
    input_names=["image"],
    output_names=["logits"],
)
print("exported")