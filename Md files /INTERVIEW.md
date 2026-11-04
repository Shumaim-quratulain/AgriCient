# 🎤 Interview / Viva — What I Did

> Short spoken answers. **Update after every milestone.**
> Last updated: 20 Oct 2026 — the **current app is described in the "Current app" section directly below**. The older answers further down describe the 17 Oct state (38-class PlantVillage model with a crop box) and are kept for the training and code questions, but **do not use them for "what does your app do"**.

---

## ✅ Current app (20 Oct 2026) — use these answers

### 30-second answer (current)
> "AgriCient is a coconut disease early-warning system. A farmer registers with their location, saves each coconut land with its own GPS pin, and can upload a palm photo for a check. The server also watches weather for each farmer's own places and forecasts a weather-based disease-risk score for the next 3, 7 and 14 days, even if nobody uploads a photo. When risk is high it automatically creates an alert, combined with any recent confident photo on that land, and notifies the farmer in the app, by device push notification and by SMS. Agricultural officers get an area-level risk map and alerts, without seeing any farmer's details. Both AI models run inside Spring Boot through ONNX Runtime."

### What is real and what is not (say this openly)
| Claim | Honest status |
|---|---|
| Photo classifier | **Real.** MobileNetV3-Small, 5 coconut conditions + NotCoconut, **94.9 % accuracy, macro-F1 0.964** on 1,351 held-out photos. It cannot say a palm is healthy and covers only 5 conditions. |
| Forecast | **Real model, but it forecasts our own weather-risk index, not real outbreaks.** We have no outbreak records, so we cannot state outbreak precision or early-warning recall. |
| 3 / 7 / 14 days | 3 days beats "same as today" slightly (MAE 0.1084 vs 0.1135). **7 days is no better than "same as today"**; 14 days is modestly better. Alerts use only the 3-day value. |
| In-app alerts | **Real and tested**, created automatically with no page open. |
| Device push | **Built and tested on the server; not yet seen on a real device.** |
| SMS | **Built end to end** (consent, one text per alert, delivery log). Real sending is limited by Twilio's free trial (stock templates only, verified numbers only), so by default it is **simulated**. In one test Twilio accepted a stock-template text and logged it as delivered; receipt on the phone was not confirmed. |
| Officers | **Real.** Area map and alerts only; they register with an invite code. |

### Likely questions about what we built after 17 Oct
| Question | Your answer |
|---|---|
| Why does your app need accounts? | So each farmer's lands and photo history are private. Every query looks up by id **and** owner, so another farmer's id returns 404. |
| Authentication vs authorization? | Authentication is proving who you are (password, token). Authorization is deciding what you may access (the owner check, the officer role). |
| How do you store passwords and tokens? | Passwords as bcrypt hashes. Session tokens are random 256-bit values; only their SHA-256 hash is stored. |
| How do you get weather for each farmer? | We round the farmer's coordinates to about 1 km to make a place key, fetch the last 7 days from Open-Meteo once per key, store it, and run the same ONNX model on it. Neighbouring farmers share one stored history. |
| What is the forecast actually predicting? | A daily risk index we defined from humidity, rain and temperature. It is a proxy, not a count of disease. |
| Why does the 7-day model not beat persistence? | Weather changes quickly and the model only sees past weather, not a forecast. At longer horizons today's weather says less. We report it and show 7 and 14 days only as a rough outlook. |
| How do you stop alert spam? | One alert per farmer per place per forecast day (a database unique key); one text and one push per alert (unique on alert + channel); texts only for Warnings and only with consent. |
| How do you combine the photo and the forecast? | A weather risk of 0.60 or more is a Warning. Between 0.50 and 0.60 a confident (>= 0.80) coconut photo **on that land** in the last 7 days raises it to a Warning. Below 0.50, such a photo still gives a Watch. |
| Do alerts need the farmer to open the app? | No. The server checks every 30 minutes and straight after a registration, new land, new phone number or new photo, and saves the alert and sends notifications. |
| What do officers see? | Areas of about 1 km, a risk level, and counts of farmers and lands. Never names, logins, phone numbers or exact land pins. |
| How does someone become an officer? | They register with a secret invite code held in a private settings file. Wrong codes are refused, and after 5 wrong tries from one address it locks for 15 minutes. If the code leaks, anyone can become an officer, so it must be kept secret. |
| Why is SMS simulated? | Twilio's free trial only allows its own templates and verified numbers, so our real wording is rejected. The sender is an interface, so a real provider is one new class. |
| Where are secrets kept? | In `~/.agricient/` (owner-only permissions), outside the project, loaded as environment variables. |
| Why gradient-boosted trees and not an LSTM, and why not Python microservices? | About 6,000 daily rows is small for a neural network, trees work well on small tables, and running both models in Spring Boot through ONNX avoids extra services. This differs from the original slides. |
| Why MySQL and a plain HTML page instead of PostgreSQL and React? | We used what we had set up and kept the frontend simple; it is a deliberate difference from the slides. |
| Did you handle class imbalance? | **No.** Training uses plain cross-entropy. We measured it (macro-F1, confusion matrix) but did not correct it with class weights. Say so plainly; it is a possible improvement. |
| What are the limitations? | No outbreak records to validate against; the forecaster saw three coconut areas only; photo test images share sources with training, so field photos may score lower; 5 conditions only and no "healthy"; push needs HTTPS or localhost; real SMS needs a paid provider. |
| Who wrote the code? | Be accurate. Per `REVISION.md`, Step 9 was first written by Copilot, moved to `backup_copilot_step9/`, and redone piece by piece with the team typing. **Much of the work after 19 Oct (accounts, per-place weather, 7/14 day models, alerts, push, SMS, officers) was written by Copilot** and has been explained but not typed by the team. |

---

## 30-second answer (17 Oct — outdated)

> *(17 Oct version — outdated, kept for history.)* "Our project is a crop disease early-warning system. A farmer uploads a leaf photo on a web page and gets a diagnosis; weather-based outbreak forecasting comes later. So far we've built the whole pipeline in Spring Boot: the photo is preprocessed in Java, run through a pretrained MobileNetV3 model with ONNX Runtime, and the result is saved in MySQL through JPA and shown on the page. That model is still the general ImageNet one, so it doesn't know crop diseases yet. We've downloaded the PlantVillage dataset — 54,305 leaf images in 38 classes — and we're doing transfer learning on it. We'll report the accuracy and F1 that we measure on the official test split."

## 90-second answer (17 Oct — outdated: describes the 38-class model and a crop box; the training explanation is still useful)

> "We followed a step-by-step plan. First the architecture: a Spring Boot backend, a web page, MySQL, and the AI model running inside the backend. Python is used only offline, for training and for exporting the model to ONNX.
>
> We started by proving the real AI works. We downloaded the official MobileNetV3-Small weights — 2.54 million parameters trained on ImageNet — and verified the file with its SHA-256 hash. Python's downloader failed with an SSL certificate error, a known macOS issue, so we used curl for the same official URL. We exported the model to ONNX so Java can run it.
>
> Then we built the backend. A `POST /api/diagnose` endpoint takes the uploaded photo, resizes and center-crops it, normalizes it with the ImageNet mean and standard deviation, runs the model through ONNX Runtime, applies softmax, and returns the top five. We checked our Java preprocessing against PyTorch and found that resizing differs, which showed why training and production preprocessing must match. Each result is saved in MySQL through JPA, with a Spring Data repository, and a history endpoint lists them. A simple web page uploads photos and shows the result.
>
> The current model only knows ImageNet's 1000 everyday classes, so it names things like 'ladybug' for a diseased leaf. That is why we're doing transfer learning: we freeze the pretrained layers, replace the last layer with 38 outputs, and train it on PlantVillage — 43,596 training and 10,709 test images, using the official split that keeps each physical leaf on one side. Classes are imbalanced, from 120 to 4,527 images, so we'll report macro-F1 and a confusion matrix as well as accuracy. We'll report only what we measure."
>
> Results we measured ourselves on the official test split: 96.4 % accuracy and 95.4 % macro-F1. Macro-F1 is lower because the small classes are weaker — `Tomato___Early_blight` has a recall of only 0.735, meaning we miss about 26 % of its cases. The confusions are between visually similar diseases of the same crop, and against `healthy`. We report both numbers because overall accuracy alone would hide the weak classes.
>
> The trained model now runs inside the Spring Boot backend. If the farmer types the crop, we restrict the classes to that crop and re-normalise, which raised the correct class on our test photo from 27 % to 90 %. If the crop is left blank, the app returns the top-5 across all crops. Below 60 % confidence it refuses instead of guessing."

---

## Likely follow-ups

| Question | Your answer |
|----------|-------------|
| Why Python at all if the backend is Java? | Only for training: PyTorch and the PlantVillage tooling are Python-first. We train once, export to ONNX, and Spring Boot runs the model with ONNX Runtime — no Python in the running system. |
| What is a pretrained model? | A network whose weights were already trained by someone else — ours on 1.2M images. We download the learned numbers instead of training from scratch. |
| What is transfer learning? | Reusing a pretrained network and retraining it on our data. Early layers know edges/shapes; we retrain the final layers on our 38 disease classes. |
| What did you download, how big? | One weights file, ~10 MB, 2.54 million parameter values. The architecture is code from torchvision; the file fills in the numbers. |
| How did you verify it? | SHA-256 hash — starts with `047dcff4`, matching PyTorch's official value. Proves complete and untampered. |
| What went wrong and how did you fix it? | Python's downloader threw an SSL certificate verification error — a known macOS issue. pip works (bundled certificates); curl works (macOS store) — we used curl on the same official URL. |

## Training and code questions (know the idea, not the syntax)

| Question | Your answer |
|----------|-------------|
| Why not train from scratch? | The pretrained model already learned edges, textures and shapes from 1.2M images. We keep that and train only a new last layer for 38 classes: far less data and time. |
| What does freezing do? | `requires_grad = False`: those weights don't change during training, so the knowledge isn't damaged. 2.5M frozen, 38,950 trainable. |
| What is the loss? | One number saying how wrong the guesses were. Training tries to make it smaller. A random guess over 38 classes is about 3.64. |
| What does `loss.backward()` do? | Backpropagation: works out which way each weight should move to reduce the loss. |
| What does `optimizer.step()` do? | Moves the weights a small step in that direction; the step size is the learning rate. |
| Learning rate too big or too small? | Too big overshoots and the loss jumps around; too small learns very slowly. |
| Epoch vs batch? | An epoch is one pass over all training photos; a batch is the 64 photos processed together. |
| Why a test set? | To measure on photos the model never learned from, otherwise the accuracy is misleading. |
| What is data leakage and how did you avoid it? | The same physical leaf in both train and test. PlantVillage's official split keeps each leaf on one side only. |
| Problems with your data? | Class imbalance (120 vs 4,527 images), so we also report macro-F1 and a confusion matrix. Also lab-style photos, so field photos may differ. |
| What is augmentation? | Random flips of training photos so the model sees different views; reduces overfitting. |
| What is overfitting? | The model memorises training photos and does badly on new ones. |
| What does softmax do? | Turns the 38 scores into probabilities that add up to 1. |
| Why is your Java preprocessing the same as training? | The model must see the same kind of numbers in training and in production: resize 256, center-crop 224, ImageNet mean and std. We compared Java with PyTorch to check. |
| Why does it call an Aadhaar card a 'web site'? | The model must pick one of its classes. ImageNet has no 'not a leaf' class. After training the same applies to our 38 classes, so we plan a confidence threshold and a 'not a leaf' class. |
| What is an ORM? | JPA with Hibernate maps a Java class to a table, an object to a row, a field to a column; a repository gives `save` and `findAll` with no hand-written SQL. |
| Why does your app ask for the crop? | The farmer knows what he planted. Naming the crop restricts the answer space — conditional classification. On our test photo it raised the correct class from 27 % to 90 %. Blank crop gives the top-5 across all crops. |
| What happens if the photo isn't a leaf? | The model must still pick one of its 38 classes, so it can name a wrong disease. We added a 60 % confidence gate that refuses below the threshold; a "not a leaf" class is future work. |
| Why is confidence low on a real field photo? | Domain shift: PlantVillage images are clean lab photos with plain backgrounds; field photos have shadows and clutter. Clean test images score 90 %+, field photos 25–40 %. A stated limitation. |
| Why do you sometimes show five results? | When no crop is given the model isn't sure, so we show its five best guesses with coverage bars and a NOT SURE label. With a crop given, we show one disease. |

## 3 phrases that sound strong

> Add: **"We say plainly what is simulated and what we could not measure."** (SMS is simulated by default; the forecast is measured against our own index, not real outbreaks.)

1. "We verified the real AI runs before building anything around it."
2. "We avoided a fake AI endpoint — the real model is in the pipeline from day one."
3. "We'll report accuracy and F1 measured on the official held-out split, not numbers from papers."

## Progress log (one line each)

- Checked tools (Java 24, Python 3.13, Node 18, git) · created venv · installed PyTorch + FastAPI
- MobileNetV3 weights downloaded, hash-verified, saved in project · real inference ran on Mac GPU
- Docs: LEARNING_PLAN · AI_PLAN · STUDY · REVISION (this file = spoken answers)
- Spring Boot project, ONNX export, model loaded in Java, `/api/diagnose` and `/api/diagnoses`, MySQL + JPA, web page: all working
- PlantVillage color images downloaded and verified (38 classes, 54,305 images); `dataset.py` and `train.py` written; quick test: loss falls (3.73 → 2.52)
- Next: full training run → evaluate (accuracy, macro-F1, confusion matrix) → export to ONNX → swap into the backend → weather and forecasting
- 19 Oct: narrowed to coconut only (5 conditions + NotCoconut, 94.9 % accuracy, macro-F1 0.964)
- 20 Oct: accounts and private lands; weather risk for each farmer's own place; 3/7/14-day models; automatic alerts with photo + forecast fusion; officer role, dashboard and alerts; invite-code sign-up; device push and SMS built (SMS simulated by default); full end-to-end check passed
- Still open: push on a real device; real SMS with our wording; outbreak validation; class-imbalance handling; git; HTTPS
