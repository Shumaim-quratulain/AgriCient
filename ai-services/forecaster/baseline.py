import numpy as np
from lags import valid, test

def mae(y, p):
    return float(np.mean(np.abs(y - p)))

print("persistence  valid MAE:", round(mae(valid["target"], valid["risk"]), 4))
print("persistence  test  MAE:", round(mae(test["target"], test["risk"]), 4))
print("always-mean  test  MAE:", round(mae(test["target"], valid["target"].mean()), 4))