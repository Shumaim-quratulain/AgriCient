import pandas as pd

WET_WEIGHT = 0.5
RAIN_WEIGHT = 0.3
TEMP_WEIGHT = 0.2
RAIN_CAP_MM = 20
TEMP_LOW = 22
TEMP_HIGH = 28


def add_risk(daily):
    wet = daily["rh90_hours"] / 24
    rain = (daily["rain_sum"] / RAIN_CAP_MM).clip(upper=1.0)
    in_band = (daily["t_mean"] >= TEMP_LOW) & (daily["t_mean"] <= TEMP_HIGH)
    temp = in_band.astype(float).where(in_band, 0.3)

    daily["risk"] = WET_WEIGHT * wet + RAIN_WEIGHT * rain + TEMP_WEIGHT * temp
    return daily