# Real inference: give it a photo path, get the network's top-5 prediction.
import sys
import time
import torch
from PIL import Image
from torchvision.models import mobilenet_v3_small, MobileNet_V3_Small_Weights

MODEL_PATH = "ai-services/models/mobilenet_v3_small.pth"

# 1. device: Mac GPU (MPS) if available, else CPU
device = torch.device("mps" if torch.backends.mps.is_available() else "cpu")

# 2. rebuild the empty architecture, then load the trained numbers into it
weights = MobileNet_V3_Small_Weights.DEFAULT
model = mobilenet_v3_small()
model.load_state_dict(torch.load(MODEL_PATH, map_location="cpu"))
model = model.to(device).eval()

# 3. open image + preprocess exactly as the pretrained weights expect
#    (resize 224x224, to numbers, normalize) -> shape [1, 3, 224, 224]
img = Image.open(sys.argv[1]).convert("RGB")
x = weights.transforms()(img).unsqueeze(0).to(device)

# 4. real forward pass through the frozen network
with torch.no_grad():
    t0 = time.perf_counter()
    logits = model(x)
    ms = (time.perf_counter() - t0) * 1000

# 5. softmax -> percentages, take top 5, print names
probs = torch.softmax(logits, dim=1)[0]
names = weights.meta["categories"]
top = torch.topk(probs, 5)
print(f"device={device.type}  inference={ms:.1f} ms  image={sys.argv[1]}")
for score, idx in zip(top.values, top.indices):
    print(f"  {names[idx]:<32} {score.item() * 100:5.2f}%")
