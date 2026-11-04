import pandas as pd

CSV = "ai-services/forecaster/data/weather.csv"
COCONUT_SITES = ["Tiptur", "Kozhikode", "Pollachi"]
CUTOFF = "2026-10-15 23:59:59"

hourly = pd.read_csv(CSV, parse_dates=["reading_time"])
hourly = hourly[hourly["location"].isin(COCONUT_SITES)]
hourly = hourly[hourly["reading_time"] <= CUTOFF]

hourly["date"] = hourly["reading_time"].dt.date
hourly["is_humid"] = hourly["humidity"] >= 90

daily = hourly.groupby(["location", "date"]).agg(
    t_mean=("temperature", "mean"),
    t_max=("temperature", "max"),
    t_min=("temperature", "min"),
    rh_mean=("humidity", "mean"),
    rh90_hours=("is_humid", "sum"),
    rain_sum=("precipitation", "sum"),
    wind_mean=("wind_speed", "mean"),
).reset_index()

print(daily.shape)
print(daily.head())