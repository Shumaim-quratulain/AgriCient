# 🧠 Agricient — Real AI Plan (no fake AI)

> Companion to `LEARNING_PLAN.md`. Answers one question: **where does the real intelligence come from at every step — and why is nothing in this app fake?**

---

## ✅ The decision

The AI is real from day one. There is **no dummy, stub or fake AI endpoint** anywhere in this plan.

| Step | What the AI actually is | Real? |
|------|-------------------------|-------|
| Step 5 — skeleton | MobileNetV3-Small, pretrained on 1.2M photos (ImageNet) | ✅ Real network, real weights, real inference on your Mac. Honest limit: its 1000 classes are general ("leaf", "cabbage"), not crop diseases yet |
| Step 6 — disease model | Same network **fine-tuned on the real PlantVillage crop-disease dataset** | ✅ Real disease names with accuracy / F1 **we measure ourselves** |

Nothing is thrown away at Step 6 — same service, same API, only the learned weights change.

---

## 🧠 The model

- `mobilenet_v3_small` from **torchvision** — lightweight (~2.5M parameters), which matches the synopsis requirement of a compact, lightweight CNN.
- Runs accelerated on your Apple Silicon Mac via **MPS** (PyTorch's GPU backend for Mac).
- Transfer learning happens in two moves (explained from zero when we reach Step 6):
  1. Replace the final layer with a 38-output layer, train **only that layer** (fast).
  2. Unfreeze the last blocks and **fine-tune** with a small learning rate (slower, more accurate).

---

## 📦 The real dataset (verified 2026-10-07)

- **`mohanty/PlantVillage`** on Hugging Face — uploaded by the authors of the original 2016 PlantVillage paper.
- **54,306 real leaf photos** · 38 classes (26 diseases + 12 healthy) · 14 crop species · color images.
- Ships **official 80/20 train/test splits that respect leaf grouping** → the same physical leaf never appears in both train and test. **No data leakage** — a strong point for the report and a common viva question we can answer confidently.
- Downloaded into `ai-services/classifier/data/` using the Hugging Face `datasets` library.

---

## 📏 Honest numbers policy

- The report shows only numbers **we measure** on the official held-out test split (accuracy, macro-F1, confusion matrix).
- Numbers from papers (e.g. 99%+) stay in the literature review, attributed to those papers — never claimed as ours.
- Expectation (not a promise): PlantVillage is clean lab imagery and transfer-learned models usually score in the high 90s. We'll find out on your machine and report exactly what we see.
- Known caveat → "field photos differ from lab photos" becomes a limitations / future-work line.

---

## 🛠️ Environment — verified on your Mac

> Exact versions of everything installed are recorded in `REVISION.md`.

| Item | Status |
|------|--------|
| macOS 26.6, Apple Silicon (arm64) | ✅ MPS (GPU) acceleration expected — to be confirmed on the first real inference run |
| Python 3.13.3 + venv at `ai-services/.venv` | ✅ |
| Java 24, Node 18, git | ✅ |
| PyTorch 2.14.1 + torchvision 0.29.1 (FastAPI also installed, now unused) | ⚠️ installed before asking — kept for now, fully removable |
| Real pretrained MobileNetV3 weights | ✅ downloaded, hash-verified, saved in `ai-services/models/` |

---

## 🗺️ Path to the actual app (maps to LEARNING_PLAN steps)

1. **Spring Boot backend (Step 1)** — Hello-World API, you type it.
2. **ONNX export + Java inference (Steps 2 + 3)** — a one-time Python script exports the weights to ONNX; Spring Boot loads it with ONNX Runtime and `/api/diagnose` returns top-5 real predictions. No Python at runtime.
3. **Database + skeleton (Steps 4 + 5)** — store farmer / upload / result; web page runs the full loop with the real model.
4. **Real disease model (Step 6)** — download PlantVillage → `train.py` → `evaluate.py` → export new ONNX file → drop into Spring Boot, real disease labels.
5. **Steps 7–10** — real weather API, real forecasting model, alert rules, officer dashboard.

---

## 📈 Step 8 — Forecaster design (decided 18 Oct 2026)

**What is forecast:** a daily **disease-pressure risk index (0–1)**, predicted **3 days ahead** from past weather only.
**Honest framing:** the index is a transparent, rule-based proxy built from agronomy guidance; the model forecasts it. The CNN (Step 6) confirms disease from a leaf photo; Step 9 fuses both. Field-validated outbreak records are future work.

**Data:** Open-Meteo hourly archive, 2021-01-01 → 2026-10-15 (~50,700 rows per location).
Locations: Tiptur, Kozhikode, Pollachi (coconut belt) + Ludhiana (wheat, very different climate — used to test generalisation).

**Split (time-based, never shuffled):** train oldest years → validation → test newest. Also hold out one **whole location** to test generalisation.

**Baseline first:** persistence ("risk in 3 days = risk today"). The model is only worth keeping if it beats this.
**Model:** gradient-boosted trees on daily features (lags, rolling means, humid-hour counts, rain totals, month). Exported to ONNX for Spring Boot.
**Metrics:** MAE vs baseline; precision/recall on high-risk days.

### Disease rules (stored as data per crop + disease, so new crops = new row)

| Crop / disease | Source | What the source confirms | What is our assumption |
|---|---|---|---|
| **Wheat — yellow (stripe) rust** | USDA-ARS Cereal Disease Lab, "Wheat stripe rust" | Infection at **0–25 °C** with **free water on the leaf**; hot/dry spells stop sporulation; latent period ≈ 11 days at mean 15 °C | Peak weighting near ~10–15 °C; "wetness" approximated by hours with RH ≥ 90 % or rain |
| **Coconut — bud rot** (*Phytophthora palmivora*) | TNAU Agritech portal, "Bud rot" | Mainly affects young palms **during the monsoon**; pre-monsoon (May, Sept) and post-onset sprays recommended | **Numeric thresholds are NOT published in the sources we could reach.** We use tunable assumptions: hours with RH ≥ 90 %, rain over last 3 and 7 days, monsoon months. Replace with CPCRI/KAU numbers if obtained. |

> Rule of honesty: any number in the "assumption" column must be described as an assumption in the report and presentation — never as a published threshold.

---

## ⚠️ The only placeholder left in the whole plan

Outbound **SMS sending**: alerts are written to the DB and shown in-app from Step 9; plugging in a real SMS gateway later is a single-file change. That's a delivery channel, not the AI — and it's stated openly in the plan, not hidden.

---

## 🔍 How we verify "real" each step

Per our working agreement: **every step ends with a run we both watch.**
Example: Step 2 is done when a real leaf photo produces a real prediction from the real model — not when the code merely compiles.
