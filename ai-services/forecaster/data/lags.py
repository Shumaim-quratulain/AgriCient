import os

import pandas as pd

from features import daily
from risk import add_risk

HORIZON = int(os.environ.get("HORIZON", "3"))

daily = add_risk(daily)
g = daily.groupby("location")

daily["target"] = g["risk"].shift(-HORIZON)

daily["risk_lag1"] = g["risk"].shift(1)
daily["risk_lag2"] = g["risk"].shift(2)
daily["rain_3d"] = g["rain_sum"].transform(lambda s: s.rolling(3).sum())
daily["rain_7d"] = g["rain_sum"].transform(lambda s: s.rolling(7).sum())
daily["rh90_3d"] = g["rh90_hours"].transform(lambda s: s.rolling(3).sum())
daily["rh90_7d"] = g["rh90_hours"].transform(lambda s: s.rolling(7).sum())
daily["month"] = pd.to_datetime(daily["date"]).dt.month

FEATURES = [
    "risk", "risk_lag1", "risk_lag2",
    "t_mean", "rh90_hours", "rain_sum",
    "rain_3d", "rain_7d", "rh90_3d", "rh90_7d",
    "month",
]

data = daily.dropna(subset=FEATURES + ["target"]).reset_index(drop=True)

data["dt"] = pd.to_datetime(data["date"])

train = data[data["dt"] < "2025-01-01"]
valid = data[(data["dt"] >= "2025-01-01") & (data["dt"] < "2026-01-01")]
test = data[data["dt"] >= "2026-01-01"]

if __name__ == "__main__":
    print("train", len(train), "valid", len(valid), "test", len(test))