# 📚 Agricient — Study Sheet

> Every concept this project will teach us, in study order.
> **Studied phases get the full explanation** (read below). Future phases are just a checklist.
> ✅ studied · ▶ studying now · ⬜ later · **Progress: 47 / 68 concepts studied**
> What we've *done* lives in `REVISION.md`. This file is only for *learning*.

---

## Phase 0 — Foundations ✅ (studied)

**client / server** — a client (browser/app) sends a request; a server processes it and replies. Every web system is just this.

**HTTP** — the request/response rules of the web: a method + a URL + maybe a body go out; a status code + body come back. `GET` = read, `POST` = send/create.

**REST** — a convention for web APIs: each URL represents a resource (`/api/diagnose`), and the HTTP method says what to do with it.

**JSON** — plain-text format for structured data, e.g. `{"disease": "late blight", "confidence": 0.93}`. The language services and apps speak to each other.

**microservice** — a small, independently running service with one job, reached over HTTP. Ours: Spring Boot backend (Java) runs the model itself; Python is used only offline for training.

## Phase 1 — How AI works ✅ (studied)

**neuron** — a small formula: `(x1·w1 + x2·w2 + … ) + b`, then an "activation" function reshapes the result. Exactly like `risk = 3*temp + 2*hum − 10*rain` you'd write in Java — except the constants are **learned, not typed**.

**weight / bias** — those constants. A weight multiplies an input; the bias is one extra constant added at the end. Millions of them = everything the network "knows".

**layer** — many neurons whose outputs become the next layer's inputs: `layer3(layer2(layer1(input)))`. Early layers find edges → later layers combine them into spots → shapes → disease. Nobody programs these steps; they emerge from training.

**forward pass** — running that chain once on one input to produce the output. Pure function call.

**CNN (Convolutional Neural Network)** — a network that slides a tiny filter (a few weights) across the whole image, instead of connecting every pixel to every neuron. Two wins: far fewer weights, and a pattern is recognised **wherever** it appears. "Convolution" = the sliding operation.

**logits** — the raw scores the last layer outputs, one number per class (1000 for ImageNet). Highest score = the network's answer.

**softmax** — converts raw scores into percentages that add to 100%: $\text{softmax}(z_i) = e^{z_i} / \sum_j e^{z_j}$. Turns a ranking into a confidence table we can display.

**inference** — using the frozen weights once to answer a question. Milliseconds; nothing is learned here. (Its opposite, **training**, is the slow tuning loop — Phase 6.)

**pretrained weights** — a saved `.pth` file of constants that someone else's training produced. We download it → real AI with zero training cost.

**ImageNet** — the 1.2M-photo / 1000-category dataset MobileNetV3 was trained on. Why it understands general things (leaf, cabbage) but not our disease names yet.

**MPS** — PyTorch's backend that runs the network on the Apple Silicon GPU, instead of the CPU.

### Self-check (answer, then verify)
1. Training vs inference? → Training = slow tuning loop on labelled data. Inference = one forward pass with frozen weights.
2. What is a weight? → A coefficient inside the network, discovered from data, not typed by a programmer.
3. Why does a CNN need fewer weights? → One small filter slides over the image and is reused at every position.
4. Why is our output not "Tomato Late Blight" yet? → Weights were trained on ImageNet's 1000 general classes, not on crop diseases. Phase 6 (transfer learning) fixes that.

### Phase 1.5 — Step 1 completed ✅ (studied)

**architecture vs weights** — the architecture is the *design* (layers, connections) = code we import. The weights are the *millions of numbers* inside it = the .pth file we downloaded. Analogy: class definition vs the values loaded into it at runtime.

**model file (.pth)** — a ~10 MB file containing **only numbers** (2.54M of them), no code. We copy it into `ai-services/models/` so the project works offline and doesn't depend on a global cache folder.

**downloading weights** — torchvision normally downloads them automatically on first use; because of the SSL issue we fetched the same official URL with curl and **verified it with its SHA-256 hash** (`047dcff4…` = PyTorch's official prefix → genuine + complete).

**SSL certificate error (macOS + python.org)** — Python from python.org doesn't see macOS's root certificates, so its HTTPS downloads are refused. pip works because it bundles its own certificates; curl works because it uses macOS's store. Fix = curl, not a new install.

**Why verify first, build later** — we proved the AI runs *before* building the app around it (like testing a DB connection before writing 50 endpoints).

**warm-up** — the first GPU inference includes one-time kernel loading (our run: 2622 ms); later calls are much faster. Normal in every ML system.

**Why the output had no disease names** — the 1000 output classes are ImageNet's general categories. Our disease classes don't exist in the output layer yet → transfer learning (Phase 6) retrains it.

## Phase 2 — Java backend + model in Java ✅ (studied)

- ✅ **Spring Boot** — the central server
- ✅ **@RestController / @GetMapping / @PostMapping** — how endpoints are declared
- ✅ **ONNX** — portable model file format Java can load (the .pth is PyTorch-only); like exporting a video to MP4
- ✅ **ONNX Runtime (Java)** — library that runs an ONNX model inside Spring Boot (`OrtEnvironment` = engine, `OrtSession` = one opened model)
- ✅ **preprocessing in Java** — resize to 256 + center-crop 224, scale to 0–1, normalize with ImageNet mean/std, shape `[1][3][224][224]`
- ✅ **multipart** — receiving an uploaded photo in Spring (`MultipartFile`, key `file`)
- ⬜ **DTO** — object shaped for the API (not the DB); we returned the entity directly for now
- ✅ **service layer** — class holding business logic (`ModelService`)

## Phase 4 — Database ✅ (studied)

- ✅ **MySQL** — the relational database we use (installed with Homebrew); the app connects as its own user `agricient`, password from the `DB_PASSWORD` environment variable
- ✅ **entity** — Java class mapped to a table (`Diagnosis` → table `diagnosis`)
- ✅ **JPA / Hibernate** — JPA is the standard, Hibernate is the ORM that does the work; translates objects ↔ tables
- ✅ **@Entity / @Id / @GeneratedValue** — mapping annotations (`@ManyToOne` comes with `Farmer`)
- ✅ **repository** — interface with ready-made `save`, `findAll`, `findById`; extra queries by method name (`findAllByOrderByCreatedAtDesc`)
- ⬜ **schema migration** — versioned table changes (we use `ddl-auto=update` for now)

## Phase 5 — Frontend (Copilot builds, you review)

- ⬜ **React** — component-based web UI
- ⬜ **component / props / state** — building blocks and data flow
- ⬜ **fetch** — browser calling our REST API
- ⬜ **FormData** — packaging a file for upload
- ⬜ **responsive design / PWA** — same site on phone and desktop

## Phase 6 — Training our real model ✅ (studied and done)

**Result we measured ourselves:** accuracy **0.9637**, macro-F1 **0.9542** on the official test split (10,709 photos the model never saw).

**transfer learning** — keep a pretrained network's knowledge (edges, textures, shapes from 1.2M photos) and train only a new last layer for our 38 classes. Needs far less data and time than training from zero.

**freezing** — `parameter.requires_grad = False`: those weights do not change during training, so what the model knows is not damaged. Ours: about 2.5M frozen, 38,950 trainable (`1024 × 38 + 38`).

**classes (38)** — the possible answers: crop + condition, e.g. `Tomato___Late_blight`, `Apple___healthy`. Sorted alphabetically they get numbers 0 to 37; the model works with numbers, the names come back from a saved list. The model must always answer with one of the 38, even for a non-leaf photo.

**loss** — one number: how wrong the guesses were (`CrossEntropyLoss`). A random guess over 38 classes gives about $\ln 38 \approx 3.64$. Training tries to make it smaller.

**backpropagation** — `loss.backward()`: works out which way each weight should move to reduce the loss.

**gradient descent / optimizer** — `optimizer.step()` (Adam): moves the weights a small step in that direction. The step size is the **learning rate** (0.001): too big overshoots, too small learns slowly.

**epoch / batch** — an epoch is one pass over all training photos; a batch is the 64 photos processed together.

**the learning step, 5 lines per batch:** guess (`model(images)`) → loss → `optimizer.zero_grad()` (clear old corrections) → `loss.backward()` → `optimizer.step()`.

**train vs test split** — the model learns from the train photos and is measured on test photos it has never seen. Official split: train 43,596, test 10,709.

**data leakage** — the same physical leaf in both train and test makes accuracy look better than it is. PlantVillage's official split keeps each leaf on one side only.

**class imbalance** — 120 images in the smallest class (`Potato___healthy`) vs 4,527 in the largest. Accuracy alone can hide weak small classes, so we also report **macro-F1** and a confusion matrix.

**augmentation** — random flips of training photos so the model sees slightly different views; reduces **overfitting** (memorising the training photos and failing on new ones). Test photos are never changed randomly.

**softmax** — turns the 38 scores into probabilities that add up to 1.

**fine-tuning** — later option: also unfreeze the last feature layers and train them with a small learning rate.

### Our Python files, in plain words

- `dataset.py` — a `Dataset` has two methods: `__len__` (how many photos) and `__getitem__(i)` (open photo `i`, resize/crop/flip/normalize it, and return the numbers with its class number). A line of the split file is the path of one image: `raw/color/<class>/<file>.JPG`; the class is the folder name (`path.split("/")[2]`).
- `train.py` — Block 1: build MobileNetV3, load the ImageNet weights, freeze, replace the last layer with 38 outputs, move to the GPU (`mps`). Block 2: DataLoaders (batches of 64, shuffled for training), loss function, optimizer, the epoch loop, accuracy on the test split, then save the weights and class list.
- The quick test `python train.py 5` runs only 5 batches per epoch and does not save a model.
- The preparation steps (resize 256, center-crop 224, mean/std) are the same in `dataset.py` and in the Java `ImagePreprocessor`: the model must see the same kind of numbers in training and in the backend.

## Phase 6b — Serving the model in the app ✅ (studied)

**closed set** — the model can only answer from its 38 classes. It has no way to say "I don't know" or "not a leaf", so an unknown plant still gets one of the 38 names.

**conditional classification** — if the farmer names the crop, we keep only that crop's classes, then re-normalise softmax over them. On our test photo this raised the correct class from **27 % to 90 %**, because the impossible crops were removed.

**confidence gate** — if the top probability is below a threshold (we chose **60 %**), the app refuses: "could not detect a disease with confidence". Stops an uncertain guess being shown as a finding.

**safety %** — the total probability of the `___healthy` classes for that crop. `risk = 100 − safety`. Labels: `SAFE` ≥ 80, `AT RISK` ≥ 50, else `HIGH RISK` (our chosen thresholds).

**domain shift** — training photos are clean lab images (one leaf, plain background); real field photos have shadows and clutter, so confidence drops. Our field/collage test scored 25–40 %; clean PlantVillage test images score 90 %+. State this as a limitation.

**why the result shows a crop name** — labels are `Crop___Condition`, e.g. `Tomato___Early_blight`. The model identifies the **condition**, not the crop. Tomato and Potato both appear for Early blight because the damage looks almost identical, not because the model identified the plant.

## Phase 7 — Weather + forecasting

- ⬜ **scheduled job** — code that runs on a timer (@Scheduled)
- ⬜ **weather API** — external service; temperature, humidity, rainfall
- ⬜ **time series** — data points indexed by time
- ⬜ **SARIMAX** — classical forecasting model (synopsis reference)
- ⬜ **LSTM** — neural network for sequences (synopsis reference)

## Phase 8 — Fusion, alerts, dashboard

- ⬜ **threshold rule** — if risk score > X → raise alert
- ⬜ **notification channel** — in-app now, SMS later
- ⬜ **dashboard** — officer view of risk across villages

## Phase 9 — Evaluation ▶ (studying now)

**Our measured result so far:** 96.4 % overall accuracy on the official test split (10,709 photos) with only the last layer trained for 3 epochs. Overall accuracy is not the whole story, because classes are imbalanced.

**accuracy** — the share of test photos answered correctly. Simple, but one big number can hide weak small classes ($accurate / total$).

**confusion matrix** — a table of size 38×38. Row = the true class, column = what the model answered. The diagonal is correct answers; everything else is a mistake, and it shows **which classes get mixed up** (e.g. Early blight vs Late blight). This is the table to show on a slide.

**precision** — of the photos the model **called** class X, how many really were X? $\frac{TP}{TP+FP}$. High precision = few false alarms.

**recall** — of all the photos that **really were** class X, how many did the model find? $\frac{TP}{TP+FN}$. High recall = few missed cases. **For plant disease, recall matters most**: a missed disease is costly.

**F1** — one number balancing precision and recall: $\frac{2 \times precision \times recall}{precision + recall}$.

**macro-F1** — the average of the 38 per-class F1 values, counting every class equally. This is why we report it: a rare class with a bad F1 pulls the macro-F1 down even when overall accuracy stays high.

## Phase 9 — Evaluation (checklist)

- ⬜ **accuracy / precision / recall / F1** — how we measure the classifier
- ⬜ **confusion matrix** — table of what got confused with what
- ⬜ **held-out test set** — photos never seen during training
- ⬜ **alert latency** — time from upload to notification
- ⬜ **honest numbers** — report our measurements; cite papers for theirs
