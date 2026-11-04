# Download the official MobileNetV3-Small weights (trained on ImageNet, 1.2M photos)
# and save a copy inside our project so the AI service never needs internet again.
import os
import torch
from torchvision.models import mobilenet_v3_small, MobileNet_V3_Small_Weights

os.makedirs("ai-services/models", exist_ok=True)

# Line below downloads ~10 MB the first time (then it's cached by torchvision)
model = mobilenet_v3_small(weights=MobileNet_V3_Small_Weights.DEFAULT)
model.eval()  # inference mode: weights frozen, no learning

n = sum(p.numel() for p in model.parameters())
torch.save(model.state_dict(), "ai-services/models/mobilenet_v3_small.pth")
print(f"Real model: {n / 1e6:.2f}M parameters, 1000 classes")
print("Saved -> ai-services/models/mobilenet_v3_small.pth")
