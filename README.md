<div align="center">

# 🥥 AgriCient

### AI-powered coconut crop care — spot leaf disease from a photo and get weather-risk alerts before trouble starts.

![Java 21](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1-6DB33F?logo=springboot&logoColor=white)
![ONNX Runtime](https://img.shields.io/badge/ONNX_Runtime-1.31-005CED?logo=onnx&logoColor=white)
![PyTorch](https://img.shields.io/badge/PyTorch-training-EE4C2C?logo=pytorch&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-database-4479A1?logo=mysql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-ready-2496ED?logo=docker&logoColor=white)
![PWA](https://img.shields.io/badge/PWA-installable-5A0FC8?logo=pwa&logoColor=white)

</div>

---

## 📖 Table of Contents

- [Overview](#-overview)
- [Key Features](#-key-features)
- [How It Works](#-how-it-works)
- [Architecture](#-architecture)
- [Tech Stack](#-tech-stack)
- [Model Performance](#-model-performance)
- [Project Structure](#-project-structure)
- [Getting Started](#-getting-started)
- [Configuration](#-configuration)
- [API Reference](#-api-reference)
- [Training the Models](#-training-the-models)
- [Deployment](#-deployment)
- [Honest Limitations](#-honest-limitations)
- [Roadmap](#-roadmap)
- [Documentation](#-documentation)

---

## 🌴 Overview

**AgriCient** helps coconut farmers and agriculture officers catch crop problems early. It combines two real, measured machine-learning models with a lightweight web app:

1. **Photo diagnosis** — take a photo of a coconut leaf and a computer-vision model tells you which of five common conditions it most likely shows (or that the photo is not a coconut at all).
2. **Weather-risk forecasting** — a forecasting model turns local weather (humidity, rain, temperature) into a disease-risk index for the next 3, 7 and 14 days, and the system sends **alerts to farmers** when risk climbs.

Everything runs on a plain CPU. Both models are exported to **ONNX** and served directly **inside the Spring Boot backend** — no separate Python server is needed in production.

> ⚠️ AgriCient gives a *possible match*, never a diagnosis, and never prescribes treatment. See [Honest Limitations](#-honest-limitations).

---

## ✨ Key Features

| | Feature | Details |
|---|---|---|
| 📷 | **Leaf photo check** | Upload a photo and get the most likely condition with confidence scores. Detects *Bud Root Dropping, Bud Rot, Gray Leaf Spot, Leaf Rot, Stem Bleeding* and rejects non-coconut images. |
| 🌦️ | **Weather-risk forecast** | 3-day main forecast plus 7- and 14-day outlook per location, from hourly weather data. |
| 🔔 | **Automatic alerts** | Scheduled jobs check risk every hour and notify farmers via **web push notifications** and **SMS**. |
| 🗺️ | **My lands** | Farmers save their plots on a map (OpenStreetMap + geocoding) and get risk per land. |
| 👨‍🌾 | **Officer dashboard** | Agriculture officers (invite-code sign-up) see risk across the areas they cover. |
| 🔐 | **Accounts & sessions** | Registration, login, SMS option, session tokens, hashed credentials. |
| 📱 | **Installable PWA** | Single-page web app with a service worker — works on phones, installable to the home screen. |
| 🛡️ | **Security-minded** | Security headers, upload size limits, no stack traces leaked to clients, container runs as non-root. |
| 🐳 | **Deploy-ready** | Multi-stage Dockerfile and an AWS deployment guide (EC2 + RDS + Caddy HTTPS). |

---

## ⚙️ How It Works

### 1. Photo diagnosis

```
Phone photo ──► Spring Boot ──► ImagePreprocessor ──► MobileNetV3-Small (ONNX) ──► class + confidence
```

A **MobileNetV3-Small** network (pretrained on ImageNet) was fine-tuned on coconut-leaf images with a `NotCoconut` rejection class (bananas, date palms, mangoes, food, cats …), so random photos aren't forced into a disease label.

### 2. Weather-risk index and forecast

Each day's risk is a transparent formula over weather:

```
risk = 0.5 × (hours with humidity ≥ 90% ÷ 24)
     + 0.3 × min(daily rain ÷ 20 mm, 1)
     + 0.2 × temperature factor      (1.0 if daily mean is 22–28 °C, else 0.3)
```

A **gradient-boosted trees** regressor (scikit-learn → ONNX) learns from lagged weather and risk to **forecast the index 3, 7 and 14 days ahead**. Weather comes from the free **[Open-Meteo](https://open-meteo.com/)** API. The Java implementation (`RiskCalc`, `RiskService`) is verified against the Python reference by a parity test.

### 3. Alerts

Scheduled jobs refresh weather for tracked sites, evaluate risk, and create alerts per area and per farmer. Delivery goes out through web push (VAPID) and SMS (Twilio, or a safe simulated "log" provider by default).

---

## 🏗️ Architecture

```
┌──────────────────────┐        ┌───────────────────────────────────────────┐
│   Browser / PWA      │  HTTPS │            Spring Boot backend            │
│  index.html + sw.js  │◄──────►│  Controllers ─► Services ─► Repositories  │
│  Leaflet / OSM map   │        │                                           │
└──────────▲───────────┘        │  ┌────────────────┐  ┌─────────────────┐  │
           │ Web Push           │  │ ModelService   │  │ RiskService     │  │
           │                    │  │ (ONNX photo    │  │ (ONNX forecast) │  │
┌──────────┴───────────┐        │  │  classifier)   │  └─────────────────┘  │
│  Push / SMS services │◄───────│  └────────────────┘  @Scheduled alert +   │
│  (VAPID, Twilio)     │        │                       weather jobs        │
└──────────────────────┘        └───────────────┬───────────────▲───────────┘
                                                │ JPA           │ HTTPS
                                        ┌───────▼──────┐  ┌─────┴───────────┐
                                        │    MySQL     │  │   Open-Meteo    │
                                        └──────────────┘  └─────────────────┘

        ai-services/ (offline, Python): train ─► evaluate ─► export_onnx ─► backend/src/main/resources
```

**Design choice:** Python is used only for *training and evaluation*. The trained models are exported to ONNX and bundled into the Java JAR, so production is a single container plus a database.

---

## 🧰 Tech Stack

| Layer | Technology |
|---|---|
| **Backend** | Java 21, Spring Boot 4.1 (Web MVC, Validation, Data JPA), Spring Security Crypto |
| **Database** | MySQL (JPA/Hibernate, schema auto-updated) |
| **ML inference** | ONNX Runtime (Java) |
| **ML training** | Python, PyTorch / torchvision (MobileNetV3-Small), scikit-learn (GradientBoosting), pandas, joblib |
| **Notifications** | Web Push (VAPID via `web-push`, BouncyCastle), Twilio SMS |
| **Frontend** | Single-page PWA (vanilla HTML/JS), service worker, Leaflet + OpenStreetMap |
| **Data sources** | Open-Meteo (weather), Nominatim (geocoding) |
| **DevOps** | Docker multi-stage build, Maven wrapper, AWS (EC2, RDS, Caddy) |

---

## 📊 Model Performance

Measured on a development laptop (CPU only). Full details in [`Md files /evaluation_results.md`](<Md files /evaluation_results.md>).

### Photo classifier (held-out test set, 1,351 images)

| Metric | Result |
|---|---|
| Overall accuracy | **94.89 %** |
| Macro-F1 | **0.964** |
| Classes | 5 coconut conditions + `NotCoconut` |

| Class | Precision | Recall | F1 |
|---|---|---|---|
| Bud Root Dropping | 1.000 | 0.981 | 0.991 |
| Bud Rot | 0.989 | 0.989 | 0.989 |
| Gray Leaf Spot | 0.948 | 0.900 | 0.923 |
| Leaf Rot | 0.875 | 0.967 | 0.919 |
| Stem Bleeding | 1.000 | 1.000 | 1.000 |
| NotCoconut | 0.994 | 0.933 | 0.963 |

### Weather-risk forecaster (mean absolute error, lower is better)

| Horizon | Our model | "Same as today" | "Always the average" |
|---|---|---|---|
| 3 days | **0.1084** | 0.1135 | 0.1776 |
| 7 days | 0.1246 | 0.1243 | 0.1778 |
| 14 days | **0.1281** | 0.1344 | 0.1779 |

At the 0.60 "Warning" threshold the 3-day model reaches **precision 0.58 / recall 0.61**; at the 0.50 "Watch" threshold, **precision 0.51 / recall 0.79**.

### Speed & footprint

| Measure | Result |
|---|---|
| Photo check (median) | ~35 ms |
| Risk request (weather cached) | ~10 ms |
| Classifier model size | ~6 MB |
| Server memory (resident) | ~266 MB |

---

## 📁 Project Structure

```
AgriCient/
├── backend/                     # Spring Boot application (Java 21)
│   ├── src/main/java/com/agricient/backend/
│   │   ├── Controller/          # REST endpoints (auth, diagnose, risk, lands, alerts, push …)
│   │   ├── Service/             # ModelService, RiskService, AlertService, WeatherService, SMS/Push
│   │   ├── Model/               # JPA entities
│   │   └── Repository/          # Spring Data repositories
│   ├── src/main/resources/
│   │   ├── static/              # PWA (index.html, sw.js)
│   │   ├── *.onnx               # Exported models bundled into the JAR
│   │   └── application.properties
│   ├── src/test/                # RiskCalc + Java/Python parity tests
│   ├── Dockerfile               # Multi-stage, non-root image
│   └── pom.xml
├── ai-services/                 # Offline ML work (Python)
│   ├── classifier/              # Dataset prep, train, evaluate, export_onnx, predict
│   ├── forecaster/              # Weather features, risk index, train, evaluate, export_onnx
│   └── models/                  # Trained weights and class lists
├── deploy/                      # AWS_GUIDE.md and agricient.env.example
├── Md files /                   # Plans, study notes, evaluation results
└── backup_copilot_step9/        # Earlier snapshot of risk/alert code
```

---

## 🚀 Getting Started

### Prerequisites

- **JDK 21**
- **MySQL 8+** (or compatible)
- *(Optional, for retraining)* Python 3.10+ with `torch`, `torchvision`, `scikit-learn`, `pandas`, `joblib`

### 1. Clone

```bash
git clone https://github.com/Shumaim-quratulain/AgriCient.git
cd AgriCient
```

### 2. Create the database

```sql
CREATE DATABASE agricient CHARACTER SET utf8mb4;
CREATE USER 'agricient'@'%' IDENTIFIED BY 'choose-a-strong-password';
GRANT ALL ON agricient.* TO 'agricient'@'%';
```

### 3. Configure

Settings are read from environment variables (see [Configuration](#-configuration)). Copy the template and fill it in:

```bash
cp deploy/agricient.env.example agricient.env   # never commit the filled-in file
export DB_PASSWORD='choose-a-strong-password'
```

### 4. Run the backend

```bash
cd backend
./mvnw spring-boot:run
```

Open **http://localhost:8080** — the PWA is served by the backend. Check health at `GET /api/health`.

### 5. Run the tests

```bash
cd backend
./mvnw test
```

### Run with Docker

```bash
cd backend
docker build -t agricient .
docker run -p 8080:8080 --env-file ../agricient.env agricient
```

---

## 🔧 Configuration

All settings are environment variables with safe defaults (defined in `application.properties`; template in [`deploy/agricient.env.example`](deploy/agricient.env.example)).

| Variable | Purpose | Default |
|---|---|---|
| `DB_URL` | JDBC URL | `jdbc:mysql://localhost:3306/agricient` |
| `DB_USER` / `DB_PASSWORD` | Database credentials | `agricient` / *(set yours)* |
| `DB_DDL` | Hibernate schema mode | `update` |
| `PORT` | HTTP port | `8080` |
| `PUSH_PUBLIC_KEY` / `PUSH_PRIVATE_KEY` | VAPID keys for web push (keep stable — changing them forces every phone to resubscribe) | – |
| `PUSH_SUBJECT` | VAPID contact (`mailto:`) | `mailto:admin@example.com` |
| `OFFICER_INVITE_CODE` | Code officers type to register; empty disables officer sign-up | *(empty)* |
| `SMS_PROVIDER` | `log` (simulate, sends nothing) or `twilio` | `log` |
| `TWILIO_ACCOUNT_SID`, `TWILIO_API_KEY_SID`, `TWILIO_API_KEY_SECRET`, `TWILIO_FROM` | Twilio credentials for real SMS | – |
| `TWILIO_TRIAL_TEMPLATE` | Message template to use on a Twilio trial | – |

> 🔒 **Never commit secrets.** Keep real values in environment variables or an untracked env file. `.env*` files are git-ignored.

---

## 🔌 API Reference

All endpoints are under `/api`.

| Area | Method & path | Description |
|---|---|---|
| **Health** | `GET /health` | Service health (use for load-balancer checks) |
| **Auth** | `POST /auth/register` · `POST /auth/login` · `POST /auth/sms` · `POST /auth/logout` | Account lifecycle |
| | `GET /auth/me` · `GET /auth/options` | Current user and sign-up options |
| **Diagnosis** | `POST /diagnose` | Upload a leaf photo (multipart, max 10 MB) |
| | `GET /diagnoses` · `DELETE /diagnoses` | Diagnosis history |
| | `GET /model` · `GET /crops` | Model info and supported crops |
| **Lands** | `GET /lands` · `POST /lands` · `DELETE /lands/{id}` | Farmer plots |
| **Risk** | `GET /risk/mine` · `GET /risk/coconut` | Risk forecast for my lands / coconut areas |
| **Weather** | `GET /weather` · `POST /weather/fetch` · `POST /weather/backfill` | Stored weather and data refresh |
| **Alerts** | `GET /alerts` · `POST /alerts/evaluate` | Area alerts and manual evaluation |
| | `GET /alerts/mine` · `GET /alerts/mine/unread-count` · `POST /alerts/{id}/read` | Personal alerts |
| **Push** | `GET /push/key` · `POST /push/subscribe` · `POST /push/unsubscribe` | Web-push subscription |
| **Officer** | `GET /officer/areas` | Areas covered by the officer |

**Example — diagnose a photo**

```bash
curl -X POST http://localhost:8080/api/diagnose \
  -F "file=@ai-services/classifier/samples/plantvillage_sample.jpg"
```

---

## 🧪 Training the Models

Training is offline and optional — trained ONNX models are already included. Scripts are run **from the repository root**.

**Photo classifier** (`ai-services/classifier/`)

```bash
python ai-services/classifier/download_model.py      # pretrained MobileNetV3-Small weights
python ai-services/classifier/prepare_coconut.py     # build the dataset splits
python ai-services/classifier/train.py               # fine-tune the classifier
python ai-services/classifier/evaluate.py            # accuracy, precision, recall, F1
python ai-services/classifier/export_onnx.py         # export to ONNX for the backend
python ai-services/classifier/predict.py <image>     # quick local prediction
```

**Risk forecaster** (`ai-services/forecaster/`)

```bash
python ai-services/forecaster/train.py               # HORIZON=3 by default
HORIZON=7  python ai-services/forecaster/train.py    # 7-day model
HORIZON=14 python ai-services/forecaster/train.py    # 14-day model
python ai-services/forecaster/evaluate.py            # compare against baselines
python ai-services/forecaster/export_onnx.py         # export to ONNX
```

Copy the exported `.onnx` files into `backend/src/main/resources/` and rebuild.

> The scripts expect the datasets under `ai-services/classifier/data/` and `ai-services/forecaster/data/`. Check each script's header for exact paths and options.

---

## ☁️ Deployment

A full, step-by-step guide lives in [`deploy/AWS_GUIDE.md`](deploy/AWS_GUIDE.md):

- **EC2** (t3.small) running the app in Docker
- **Caddy** in front for automatic free HTTPS (required for GPS and push notifications)
- **RDS MySQL** for the database

> Run **one server only** — the scheduled alert jobs would otherwise run twice. The guide notes the Docker image and AWS steps are not yet tested on AWS, so expect small fixes.

---

## ⚠️ Honest Limitations

AgriCient is built to be honest about what it can and cannot do:

1. **A photo result is a possible match, not a diagnosis.** The app never prescribes treatment. Gray Leaf Spot and Leaf Rot are sometimes confused (42 + 11 test images).
2. **The forecast is measured against our own risk formula, not real outbreaks.** There are no historical outbreak records yet, so real-world predictive power is unproven.
3. **The model beats simple baselines only slightly.** The 7-day model is no better than "same as today"; the 7- and 14-day values are a rough outlook. Alerts use only the 3-day value.
4. **Trained on three coconut areas** (Tiptur, Kozhikode, Pollachi). Other places are a guide only.
5. **Accuracy is on the dataset's test split.** Phone photos in the field may be harder.
6. **Covers five coconut conditions only**, not every coconut disease or pest.
7. **Timings** are from one laptop with one user — not a load test.

---

## 🗺️ Roadmap

- [ ] Validate risk forecasts against real outbreak records
- [ ] Field-photo evaluation with farmers' phones
- [ ] Load testing with many concurrent users
- [ ] More crops and conditions
- [ ] Tested AWS deployment and CI/CD pipeline
- [ ] Measure alert delivery time end to end

---

## 📚 Documentation

| Document | What's inside |
|---|---|
| [`Md files /evaluation_results.md`](<Md files /evaluation_results.md>) | Full model evaluation and caveats |
| [`Md files /AI_PLAN.md`](<Md files /AI_PLAN.md>) | How the real AI was planned and built |
| [`Md files /LEARNING_PLAN.md`](<Md files /LEARNING_PLAN.md>) | Step-by-step project learning plan |
| [`Md files /STUDY.md`](<Md files /STUDY.md>) · [`REVISION.md`](<Md files /REVISION.md>) · [`INTERVIEW.md`](<Md files /INTERVIEW.md>) | Study, revision and interview notes |
| [`deploy/AWS_GUIDE.md`](deploy/AWS_GUIDE.md) | AWS deployment guide |

---

<div align="center">

Made with 🥥 for coconut farmers.

**[⬆ Back to top](#-agricient)**

</div>
