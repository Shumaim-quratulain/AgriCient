import joblib
import numpy as np
from skl2onnx import convert_sklearn
from skl2onnx.common.data_types import FloatTensorType

from lags import FEATURES, HORIZON, test

suffix = "" if HORIZON == 3 else f"_{HORIZON}"
model = joblib.load(f"ai-services/forecaster/risk_model{suffix}.joblib")

onnx_model = convert_sklearn(
    model,
    initial_types=[("features", FloatTensorType([None, len(FEATURES)]))],
    target_opset=17,
)

path = f"backend/src/main/resources/risk_model{suffix}.onnx"
with open(path, "wb") as f:
    f.write(onnx_model.SerializeToString())

import onnxruntime as ort
session = ort.InferenceSession(path)
sample = test[FEATURES].head(5).values.astype(np.float32)
onnx_out = session.run(None, {"features": sample})[0].ravel()
sk_out = model.predict(test[FEATURES].head(5))
print("sklearn:", np.round(sk_out, 4))
print("onnx   :", np.round(onnx_out, 4))
print("max difference:", float(np.max(np.abs(sk_out - onnx_out))))