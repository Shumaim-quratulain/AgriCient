"""Collect small 'NotCoconut' sets from Hugging Face datasets.

Output layout (images shortest side <= 256 px, same as the coconut photos):
  data/multi/NotCoconut___<source>/NNNNN.jpg      used for training and the grouped split
  data/notcoconut_holdout/<source>/NNNNN.jpg      NEVER used for training, only to measure rejection

Images are streamed and only the first N are kept, so nothing large is downloaded.
Which sources go where is decided here and nowhere else.
"""
import os

HERE = "ai-services/classifier/data"
TRAIN_OUT = os.path.join(HERE, "multi")
HOLD_OUT = os.path.join(HERE, "notcoconut_holdout")
IMG_SIDE = 256
os.environ["HF_HUB_CACHE"] = os.path.abspath(os.path.join(HERE, "hf-hub"))

from datasets import load_dataset
from PIL import Image

# (name, repo, config, split, number kept, 'train' or 'holdout')
SOURCES = [
    ("Tomato",     "Project-AgML/tomato_leaf_disease",                    None, "train", 150, "train"),
    ("Banana",     "Project-AgML/banana_leaf_disease_classification",     None, "train", 150, "train"),
    ("Mango",      "Project-AgML/mango_leaf_disease_classification",      None, "train", 150, "train"),
    ("DatePalm",   "Project-AgML/date_palm_leaf_disease_classification",  None, "train", 150, "train"),
    ("Cats",       "microsoft/cats_vs_dogs",                              None, "train", 150, "train"),
    ("Food",       "ethz/food101",                                        None, "train", 150, "train"),
    # never trained on: a different plant, a different leaf crop, and a non-leaf set
    ("H_Sugarcane", "Project-AgML/sugarcane_leaf_disease_classification", None, "train", 60, "holdout"),
    ("H_Paddy",     "Project-AgML/paddy_disease_classification",          None, "train", 60, "holdout"),
    ("H_PalmFruit", "Project-AgML/oil_palm_fruit_ripeness_classification", None, "train", 60, "holdout"),
]


def save_image(picture, path):
    rgb = picture.convert("RGB")
    rgb.thumbnail((IMG_SIDE, IMG_SIDE), Image.LANCZOS)
    rgb.save(path, quality=92)


def main():
    for name, repo, config, split, keep, kind in SOURCES:
        folder = (os.path.join(TRAIN_OUT, f"NotCoconut___{name}") if kind == "train"
                  else os.path.join(HOLD_OUT, name))
        os.makedirs(folder, exist_ok=True)
        kwargs = {"split": split, "streaming": True}
        if config:
            kwargs["name"] = config
        try:
            stream = load_dataset(repo, **kwargs)
            # take a spread, not the first N (classes are often stored in order)
            stream = stream.shuffle(seed=42, buffer_size=2000)
            n = 0
            for example in stream:
                save_image(example["image"], os.path.join(folder, f"{n:05d}.jpg"))
                n += 1
                if n >= keep:
                    break
            print(f"{kind:<8}{name:<14}{n:>4} images  <- {repo}", flush=True)
        except Exception as error:
            print(f"FAILED  {name}: {type(error).__name__}: {str(error)[:120]}", flush=True)


if __name__ == "__main__":
    main()
