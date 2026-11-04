import os

import joblib
import numpy as np
from sklearn.ensemble import GradientBoostingRegressor

from lags import FEATURES, HORIZON, train, valid, test

def mae(y, p):
    return float(np.mean(np.abs(y - p)))

model = GradientBoostingRegressor(
    n_estimators=300,
    learning_rate=0.05,
    max_depth=3,
    random_state=42,
)
model.fit(train[FEATURES], train["target"])

print("model        valid MAE:", round(mae(valid["target"], model.predict(valid[FEATURES])), 4))
print("persistence  valid MAE:", round(mae(valid["target"], valid["risk"]), 4))

name = "risk_model.joblib" if HORIZON == 3 else f"risk_model_{HORIZON}.joblib"
print("horizon:", HORIZON, "days; saving", name)
joblib.dump(model, f"ai-services/forecaster/{name}")