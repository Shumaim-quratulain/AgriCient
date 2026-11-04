# AgriCient: Evaluation Results

Measured on 20 October 2026 on one development laptop (CPU only, one user, MySQL and the Spring Boot server on the same machine).

> Read the "What these numbers do NOT show" section before quoting anything.

---

## 1. Photo classifier (coconut leaf photo check)

Source: `ai-services/classifier/eval_coconut6.txt`, produced by `evaluate.py` on a held-out test split.

| Measure | Result |
|---|---|
| Held-out test images | 1,351 |
| Overall accuracy | 94.89% |
| Macro-F1 | 0.964 |
| Classes | 5 coconut conditions + "NotCoconut" |

| Class | Precision | Recall | F1 | Test images |
|---|---|---|---|---|
| Bud Root Dropping | 1.000 | 0.981 | 0.991 | 108 |
| Bud Rot | 0.989 | 0.989 | 0.989 | 94 |
| Gray Leaf Spot | 0.948 | 0.900 | 0.923 | 429 |
| Leaf Rot | 0.875 | 0.967 | 0.919 | 334 |
| Stem Bleeding | 1.000 | 1.000 | 1.000 | 206 |
| NotCoconut | 0.994 | 0.933 | 0.963 | 180 |

Main confusion: 42 Gray Leaf Spot photos were answered as Leaf Rot, and 11 Leaf Rot photos as Gray Leaf Spot.

---

## 2. Weather-risk forecast (3, 7 and 14 days ahead)

Source: `ai-services/forecaster/evaluate.py`. Test period: 1 January 2026 onward, three coconut areas (Tiptur, Kozhikode, Pollachi), 855 test days. Model: gradient-boosted trees, exported to ONNX.

| Method | Mean absolute error |
|---|---|
| Always guess the average | 0.1774 |
| Assume the risk stays the same as today | 0.1135 |
| **Our model** | **0.1084** |

| Alert threshold | Precision | Recall |
|---|---|---|
| 0.60 (Warning level) | 0.58 | 0.61 |
| 0.50 (Watch level) | 0.51 | 0.79 |
| 0.45 | 0.45 | 0.89 |

There are 87 "true high" days (index of 0.60 or more) in the test set.

### 7 and 14 day horizons

The same method, trained separately for each horizon (`HORIZON=7` and `HORIZON=14`). Mean absolute error, lower is better.

| Horizon | Test days | Our model | Same as today | Always the average | Improvement over average |
|---|---|---|---|---|---|
| 3 days | 855 | 0.1084 | 0.1135 | 0.1776 | 39% |
| 7 days | 843 | 0.1246 | 0.1243 | 0.1778 | 30% |
| 14 days | 822 | 0.1281 | 0.1344 | 0.1779 | 28% |

| Horizon | Precision at 0.60 | Recall at 0.60 | Precision at 0.50 | Recall at 0.50 |
|---|---|---|---|---|
| 3 days | 0.58 | 0.61 | 0.51 | 0.79 |
| 7 days | 0.60 | 0.75 | 0.49 | 0.87 |
| 14 days | 0.53 | 0.66 | 0.43 | 0.88 |

Notes:
- The 7 day model is **no better than assuming tomorrow's risk equals today's** (0.1246 against 0.1243). It beats only the average.
- All three models also beat a "this calendar month's average" guess (about 0.17), so they carry more than the season.
- The app shows the 3 day value as the main forecast and uses only that for alerts. The 7 and 14 day values are shown as a rough outlook.
- Java and Python give the same values for all three models (checked for one place).

---

## 3. Speed and resources

| Measure | Result |
|---|---|
| Photo check, median of 10 requests (28 KB image) | about 35 ms |
| Photo check, fastest / slowest | 29 ms / 446 ms (the first request) |
| Weather-risk request, weather already stored | 9 to 10 ms |
| Weather-risk request, weather downloaded first | about 855 ms |
| Full alert check for one farmer with two places, first run | 471 ms |
| Same check, second run | 59 ms |
| Photo classifier model files | about 6 MB |
| Server memory in use (resident) | about 266 MB |

The automatic alert check runs every hour, so the delay from new weather to a saved alert is up to an hour plus about half a second.

---

## What these numbers do NOT show

1. **The forecast is measured against our own weather-risk index, not real disease outbreaks.** The index is a formula over humidity, rain and temperature. "True high day" means the formula gave 0.60 or more. We have no historical outbreak records, so we cannot state how well the system predicts real outbreaks or how early it warns before a first farmer report.
2. **The model beats the simple baselines only slightly** (0.1084 against 0.1135 at 3 days, and no better than "same as today" at 7 days). The honest summary is "slightly better than assuming no change"; the longer horizons are a rough outlook.
3. **The forecaster was built on weather from three coconut areas.** Other places are a guide only.
4. **The classifier accuracy is on the dataset's test split.** Phone photos taken in the field may be harder.
5. **Timings are from one laptop with one user.** They show the model is light enough for a CPU. They do not show behaviour under heavy load. The photo median uses only 10 requests.
6. **A single photo is a possible match, not a diagnosis.** The classifier confuses Gray Leaf Spot and Leaf Rot in some cases. The app never prescribes treatment.
7. The photo classifier covers five coconut conditions only. It does not recognise every coconut disease or pest.

## Not yet measured

- Accuracy against real outbreaks.
- Alert delivery time through the hourly scheduled job (the alert check was timed directly).
- Behaviour with many users at once.
