# 🌱 Agricient — Learning & Build Plan

> **AI-Driven Predictive Early-Warning System for Crop Disease Outbreaks**
> How we will build this project: one concept at a time, one working piece at a time.

---

## 🤝 Our Working Agreement

1. **Concept first** — the idea is explained in plain words before any code.
2. **You type the code** — backend & AI code is typed by the team, guided line-by-line by Copilot.
3. **We run it together** — nothing is "done" until we see it working.
4. **Only then next step** — no code lands in the project that we don't understand.
5. **Frontend is written by Copilot** (team focus = backend + AI).

**Strategy: the "Walking Skeleton"** — first make the *thinnest possible end-to-end pipeline* work (photo in → result out) using a **real pretrained neural network** from the start: real weights, real inference, no dummy endpoint. Then make each part smarter, one at a time. Something real runs from Step 5 onward.

> Where the real AI comes from at every step (and why nothing here is fake) → see **`AI_PLAN.md`**.

---

## 📋 The Steps

| # | Build | Concepts learned | Status |
|---|-------|------------------|--------|
| 0 | Setup + big picture | client/server, HTTP, REST, JSON, microservices; install tools | ✅ complete |
| 1 | Hello-World Spring Boot API | backend, controllers, endpoints, JSON responses | ✅ complete |
| 2 | Export the model to ONNX (one-time Python script) | what ONNX is, why Java can't load .pth directly | ✅ complete |
| 3 | Run the model inside Spring Boot | ONNX Runtime for Java, image preprocessing in Java, `/api/diagnose` | ✅ complete |
| 4 | Database + JPA | tables, entities, JPA vs SQL; store farmers & results | ✅ complete |
| 5 | **End-to-end skeleton** 🎉 | full pipeline running: web page → upload photo → **real pretrained CNN** (MobileNetV3) → result shown | ✅ complete |
| 6 | **Real crop-disease CNN** | transfer learning on real PlantVillage data (54,306 real leaf photos, 38 classes), fine-tuning, accuracy + F1 measured on official held-out split | ✅ complete — 96.4 % acc, 0.954 macro-F1 |
| 6b | **Multi-crop expansion** (added by us) | combining many datasets, label harmonisation, class imbalance, disk/memory constraints | ✅ done, then **narrowed to coconut only on 19 Oct** (see "What changed from the original plan" below). The 331-class model is kept in `ai-services/models/backup_331/`. Live model: 6 outputs (5 coconut conditions + NotCoconut), 94.9 % accuracy, macro-F1 0.964 on 1,351 held-out photos |
| 7 | Weather ingestion job | scheduled jobs, calling a weather API, storing time-series | ✅ complete |
| 8 | Real forecasting model | time-series basics, training the forecaster | ✅ complete (beats persistence by 4.5 % on test) |
| 9 | Fusion + alert engine | threshold rules, alert decisions, notifications (log → SMS later) | ✅ complete; rebuilt on 20 Oct to alert **each farmer's own places** (see Steps 11–18). SMS code exists but sending is simulated |
| 10 | Officer dashboard + evaluation | charts, metrics, alert latency measurement | ✅ dashboard + map built; ✅ evaluation measured (`evaluation_results.md`); ⚠️ alert **delivery** latency not measured end to end |
| 11 | Farmer accounts, private lands, per-land photo checks | authentication vs authorization, bcrypt, session tokens, ownership checks | ✅ complete (20 Oct) |
| 12 | Weather risk for each farmer's own coordinates | geolocation, place keys, caching weather per ~1 km area | ✅ complete (20 Oct) |
| 13 | 3-, 7- and 14-day forecasts | one model per horizon, baselines, honest comparison | ✅ complete (20 Oct); 7 day is no better than "same as today" |
| 14 | Automatic in-app alerts + photo/forecast fusion per land | scheduled jobs, one alert per place per day, background checks | ✅ complete (20 Oct) |
| 15 | Officer role, officer alerts, invite-code sign-up | roles, data minimisation, brute-force limit | ✅ complete (20 Oct) |
| 16 | Device push notifications (Web Push) | service worker, VAPID keys, subscriptions | 🔄 built and recorded; **never seen on a real device** |
| 17 | SMS alerts | provider interface, consent, audit log | 🔄 built; **real sending blocked by Twilio's free trial** (templates only, verified numbers only). In one test Twilio accepted a template text and logged it as delivered; receipt on the phone was not confirmed |
| 18 | Real-outbreak validation of the forecast | needs historical outbreak records | ⬜ not possible with the data we have |

✅ = working and verified · 🔄 = built but not fully proven · ⬜ = not started

> **Dependency note:** step 8 (forecasting) cannot start before step 7 (weather
> ingestion), because the forecaster trains on the stored weather time-series.
> Step 9 then fuses the CNN diagnosis with the weather risk.

---

## 🔀 What changed from the original plan (read this before presenting)

| Original plan / presentation | What was actually built |
|---|---|
| Many crops (PlantVillage, 38 then 331 classes) | **Coconut only** (5 conditions + "NotCoconut"). The multi-crop work is kept as a backup. |
| Python microservices (Flask/FastAPI) for the AI | Both models run **inside Spring Boot with ONNX Runtime**. Python is only used offline for training and export. |
| LSTM forecaster | **Gradient-boosted trees**, one model each for 3, 7 and 14 days |
| PostgreSQL | **MySQL** |
| React frontend | Plain **HTML, CSS and JavaScript** page served by Spring Boot |
| Fixed weather sites | Weather is fetched **for each farmer's own coordinates** (rounded to ~1 km) |
| Alerts to the region | Alerts **per farmer and per land**, plus alerts to **officers** |
| SMS gateway | Code written for Twilio; real sending limited by the free trial. A simulated sender is the default. |
| "Farmer registers with village" | Name, password, and location by browser GPS or typed coordinates |
| Class imbalance handled (SMOTE / class weights) | **Not done.** Training uses plain cross-entropy. Imbalance is only *measured* with macro-F1 and a confusion matrix. |

---

## 🧰 Tools we will install (as needed, not all at once)

| Tool | Used for | Needed from step |
|------|----------|------------------|
| Java (JDK 21+) | Spring Boot backend | 1 |
| ~~Node.js + npm~~ | not used: the frontend is plain HTML/JS | 5 |
| Python 3 (3.10+) | one-time training + ONNX export scripts only (not a running service) | 2 |
| ~~PostgreSQL~~ MySQL | main database (MySQL is what we used) | 4 |
| Git | version control | 0 |
| VS Code extensions | Java + Spring Boot + Python support | 0 |

---

## 📁 Planned project structure (will grow one step at a time)

```
Agricient/
├── backend/          ← Spring Boot (Java) — the central manager
├── ai-services/      ← Python training/export scripts (offline, run once)
│   ├── classifier/   ← CNN leaf-disease training + ONNX export
│   └── forecaster/   ← outbreak-risk forecaster training + export
├── frontend/         ← React web app (farmer + officer dashboard)
└── docs/             ← this plan, synopsis, notes
```
