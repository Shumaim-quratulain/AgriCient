import joblib
import numpy as np

from lags import FEATURES, HORIZON, test, valid

def mae(y, p):
    return float(np.mean(np.abs(y - p)))

name = "risk_model.joblib" if HORIZON == 3 else f"risk_model_{HORIZON}.joblib"
model = joblib.load(f"ai-services/forecaster/{name}")
print("horizon:", HORIZON, "days")
pred = model.predict(test[FEATURES])
y = test["target"].values
pers = test["risk"].values
mean_guess = np.full(len(y), float(valid["target"].mean()))

print("test rows:", len(y))
print("always-mean MAE :", round(mae(y, mean_guess), 4))
print("persistence MAE :", round(mae(y, pers), 4))
print("model MAE       :", round(mae(y, pred), 4))

high = y >= 0.6
for thr in (0.6, 0.5, 0.45):
    alert = pred >= thr
    hits = (alert & high).sum()
    print("threshold", thr, "precision", round(hits / max(alert.sum(), 1), 2),
          "recall", round(hits / high.sum(), 2),
          "(true high days:", int(high.sum()), ")")