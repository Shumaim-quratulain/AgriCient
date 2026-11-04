"""Download crop-disease datasets and lay them out as data/multi/<Crop>___<Condition>/<n>.jpg.

Class names come from each dataset's own label column, so nothing is hard-coded.
Only raw configurations are used: augmented versions repeat images.
The Hugging Face cache for each dataset is deleted after its images are saved.
"""
import os
import re
import shutil

OUT = "ai-services/classifier/data/multi"
CACHE = os.path.abspath("ai-services/classifier/data/hf-cache")
HUB = os.path.abspath("ai-services/classifier/data/hf-hub")

# Raw downloads land in a flat, hash-named `hub/blobs` folder. Nothing in it
# carries the repo name, so name-matching never found it and ~21 GB leaked.
# Point it at a folder we own so the whole thing can be wiped after each dataset.
# This must be set before `huggingface_hub` / `datasets` are imported.
os.environ["HF_HUB_CACHE"] = HUB

from PIL import Image
from datasets import load_dataset
MIN_FREE_GB = 4

# The training transform is Resize(256) -> CenterCrop(224), so any pixel beyond
# 256 on the shorter side is thrown away anyway. Storing full-resolution photos
# wasted ~90% of the disk (148 KB average per image).
IMG_SIDE = 256

MANIFEST = os.path.join(OUT, "_sources.txt")

# Repositories whose images are already on disk from earlier runs.
ALREADY_DONE = [
    "Project-AgML/rice_leaf_disease_classification_india",
    "Project-AgML/coconut_tree_disease_classification",
    "Project-AgML/sugarcane_leaf_disease_classification",
    "Project-AgML/cotton_leaf_disease_classification_bangladesh_2",
    "Project-AgML/banana_leaf_disease_classification",
    "Project-AgML/mango_leaf_disease_classification",
    "Project-AgML/groundnut_leaf_disease_classification",
    "Project-AgML/turmeric_leaf_disease_classification",
    "Project-AgML/black_gram_disease_classification",
    "Project-AgML/COLD_chili_leaf_disease_classification",
]

# (crop, repo_id, config or None)
# Everything below is image-classification with one crop per dataset, so the
# <Crop>___<Condition> folder scheme stays unambiguous. Datasets whose label
# column mixes several crops (TOM2024, AgriVision4, MedLeafX, PlantDoc...)
# are deliberately excluded, as are object-detection / segmentation sets.
DATASETS = [
    # cereals / grains
    ("Rice", "Project-AgML/paddy_disease_classification", None),
    ("Rice", "Project-AgML/rice_leaf_disease_classification", None),
    ("Rice", "Project-AgML/rice_disease_classification_bangladesh", "raw"),
    ("Maize", "Project-AgML/corn_maize_leaf_disease", None),
    ("Maize", "Spitblaze/Corn_or_Maize_Leaf_Disease_Dataset", None),
    # pulses (dal) and oilseeds
    ("Lentil", "Project-AgML/lentil_disease_classification", None),
    ("Cowpea", "Project-AgML/bean_cowpea_leaf_disease_classification", None),
    ("Blackgram", "Project-AgML/black_gram_disease_classification", None),
    ("Blackgram", "Project-AgML/blackgram_plant_leaf_disease_classification", None),
    ("Bean", "Project-AgML/bean_disease_uganda", None),
    ("Soybean", "Project-AgML/soybean_leaf_disease_classification", None),
    ("Soybean", "Project-AgML/soybean_insect_classification", None),
    ("Groundnut", "Project-AgML/groundnut_leaf_disease_classification", None),
    ("Groundnut", "Project-AgML/groundnut_leaf_disease_classification_2", None),
    ("Sunflower", "Project-AgML/sunflower_disease_classification", None),
    # plantation / cash crops
    ("Coconut", "Project-AgML/coconut_tree_disease_classification", None),
    ("Sugarcane", "Project-AgML/sugarcane_leaf_disease_classification", None),
    ("Cotton", "Project-AgML/cotton_leaf_disease_classification_bangladesh_2", "raw"),
    ("Jute", "Project-AgML/jute_disease_classification", None),
    ("Coffee", "Project-AgML/arabica_coffee_leaf_disease_classification", None),
    ("Tea", "Project-AgML/tea_leaf_disease_classification_bangladesh", None),
    # fruit
    ("Banana", "Project-AgML/banana_leaf_disease_classification", None),
    ("Mango", "Project-AgML/mango_leaf_disease_classification", None),
    ("Papaya", "Project-AgML/papaya_leaf_disease_classification_bangladesh", "raw"),
    ("Guava", "Project-AgML/guava_disease_classification", "raw"),
    ("Guava", "Project-AgML/guava_disease_pakistan", None),
    ("Pomegranate", "Project-AgML/pomegranate_disease_classification_india", None),
    ("Lemon", "Project-AgML/lemon_leaf_disease_classification", "raw"),
    ("Orange", "Project-AgML/orange_leaf_disease_classification", None),
    ("Apple", "Project-AgML/apple_leaf_disease_classification", None),
    ("Grape", "Project-AgML/grape_leaf_disease_classification", None),
    ("Plum", "Project-AgML/plum_leaf_fruit_disease_classification", "raw"),
    ("Javaplum", "Project-AgML/java_plum_leaf_disease_classification", None),
    ("Hogplum", "Project-AgML/hog_plum_leaf_disease_classification", "raw"),
    ("Carambola", "Project-AgML/carambola_disease_classification", "raw"),
    ("Dragonfruit", "Project-AgML/dragonfruit_disease_classification", "raw"),
    ("Durian", "Project-AgML/durian_disease_classification_vietnam", None),
    ("Datepalm", "Project-AgML/date_palm_leaf_disease_classification", None),
    ("Custardapple", "Project-AgML/custard_apple_disease_classification", None),
    ("Fig", "Project-AgML/fig_leaf_ficus_worm_classification", None),
    # vegetables
    ("Tomato", "Project-AgML/tomato_leaf_disease", None),
    ("Potato", "Project-AgML/potato_leaf_disease_classification", None),
    ("Cucumber", "Project-AgML/cucumber_disease_classification", None),
    ("Watermelon", "Project-AgML/watermelon_disease_classification", "raw"),
    ("Chilli", "Project-AgML/COLD_chili_leaf_disease_classification", "raw"),
    ("Onion", "Project-AgML/COLD_onion_leaf_disease_classification", "raw"),
    ("Brinjal", "Project-AgML/eggplant_disease_classification", "raw"),
    ("Brinjal", "Project-AgML/BrinjalFruitX_disease_classification", None),
    ("Cauliflower", "Project-AgML/cauliflower_leaf_disease_classification", None),
    ("Spinach", "Project-AgML/IDDMSLD_spinach_leaf_disease_classification", None),
    ("Radish", "Project-AgML/radish_leaf_disease_classification", None),
    ("Ashgourd", "Project-AgML/ash_gourd_disease_classification", None),
    ("Taro", "Project-AgML/taro_blight_stage_classification", None),
    # spices / herbs / other
    ("Turmeric", "Project-AgML/turmeric_leaf_disease_classification", "raw"),
    ("Moringa", "Project-AgML/MoringaLeafNet_disease_classification", "raw"),
    ("Bayleaf", "Project-AgML/bay_leaf_disease_classification", None),
    ("Betel", "Project-AgML/betel_leaf_disease_classification_2", "raw"),
    ("Mint", "Project-AgML/mint_leaf_classification", None),
    ("Lemongrass", "Project-AgML/lemongrass_disease_classification", None),
    ("Centella", "Project-AgML/centella_asiatica_leaves", None),
    ("Rose", "Project-AgML/RoseLeafInsight_disease_classification", None),
    ("MoneyPlant", "Project-AgML/money_plant_disease_classification", None),
]


def clean(text):
    return "_".join(str(text).strip().split())


def save_image(picture, path):
    """Store the shortest side as IMG_SIDE; thumbnail() never upscales."""
    rgb = picture.convert("RGB")
    rgb.thumbnail((IMG_SIDE, IMG_SIDE), Image.LANCZOS)
    rgb.save(path, quality=92)


def free_gb():
    stats = os.statvfs("/Users/shumaimquratualain")
    return stats.f_bavail * stats.f_frsize / 1024 ** 3


def cache_key(text):
    """Normalise a cache folder name so any separator style compares equal.

    Repo ids use '/', folder names use '-', '_' or '--', so fold them all.
    """
    return re.sub(r"[-_/]+", "_", text).lower()


def drop_cache(repo_id):
    """Delete the raw and processed cache for one dataset.

    The `datasets` library names folders several ways depending on version
    (`Project-AgML___name`, `datasets--Project-AgML--name`, ...). An earlier
    version of this function assumed `datasets--repo--name`, never matched, and
    silently leaked gigabytes of cache, so match on the normalised repo key.
    """
    # both folders are ours and hold nothing but this run's download
    for folder in (HUB, CACHE):
        shutil.rmtree(folder, ignore_errors=True)
        os.makedirs(folder, exist_ok=True)

    wanted = cache_key(repo_id)
    roots = [
        os.path.expanduser("~/.cache/huggingface/datasets"),
    ]
    for root in roots:
        if not os.path.isdir(root):
            continue
        for name in os.listdir(root):
            if wanted not in cache_key(name):
                continue
            target = os.path.join(root, name)
            if os.path.isdir(target):
                shutil.rmtree(target, ignore_errors=True)
            else:
                try:
                    os.remove(target)
                except OSError:
                    pass


def fetch(crop, repo_id, config):
    kwargs = {"split": "train", "cache_dir": CACHE}
    if config:
        kwargs["name"] = config
    ds = load_dataset(repo_id, **kwargs)
    names = ds.features["label"].names
    counts = {}
    for example in ds:
        folder = os.path.join(OUT, f"{crop}___{clean(names[example['label']])}")
        os.makedirs(folder, exist_ok=True)
        n = counts.get(folder, 0)
        save_image(example["image"], os.path.join(folder, f"{n:05d}.jpg"))
        counts[folder] = n + 1
    return counts


def load_manifest():
    done = set(ALREADY_DONE)
    if os.path.exists(MANIFEST):
        with open(MANIFEST) as handle:
            done.update(line.strip() for line in handle if line.strip())
    return done


def save_manifest(done):
    with open(MANIFEST, "w") as handle:
        handle.write("\n".join(sorted(done)) + "\n")


def main():
    os.makedirs(OUT, exist_ok=True)
    os.makedirs(CACHE, exist_ok=True)
    # a repository is skipped only when it was fetched before, so a crop may
    # still receive images from a second dataset on a later run
    done = load_manifest()
    total = 0
    for crop, repo_id, config in DATASETS:
        if repo_id in done:
            print(f"\n== {crop}: {repo_id} already fetched, skipping", flush=True)
            continue
        if free_gb() < MIN_FREE_GB:
            print(f"\nSTOPPING: only {free_gb():.1f} GB free", flush=True)
            break
        print(f"\n== {crop}: {repo_id}  (free {free_gb():.1f} GB)", flush=True)
        try:
            counts = fetch(crop, repo_id, config)
        except Exception as error:
            print(f"   FAILED: {type(error).__name__}: {error}", flush=True)
            continue
        finally:
            drop_cache(repo_id)
        saved = sum(counts.values())
        total += saved
        for folder, n in sorted(counts.items()):
            print(f"   {os.path.basename(folder):<48}{n:>5}", flush=True)
        print(f"   saved {saved} images", flush=True)
        done.add(repo_id)
        save_manifest(done)
    print(f"\nTOTAL saved this run: {total}")


if __name__ == "__main__":
    main()
