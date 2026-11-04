"""Shrink every image under a data folder so its shorter side is at most 256 px.

The training transform is Resize(256) -> CenterCrop(224), so larger images cost
disk but add nothing. Photos are re-saved as JPEG; an original PNG/JPEG is
removed once its JPEG replacement is written, so this rewrites files in place.

Every source is re-downloadable, so this is safe to run, but it is still a
destructive edit -- check the printed total before using it.
"""
import os
import sys

from PIL import Image

IMG_SIDE = 256
JPEG_QUALITY = 92
SUFFIXES = (".jpg", ".jpeg", ".png", ".bmp", ".webp")


def shrink(root):
    before = after = files = skipped = 0
    for folder, _, names in os.walk(root):
        for name in names:
            if not name.lower().endswith(SUFFIXES):
                continue
            path = os.path.join(folder, name)
            try:
                size = os.path.getsize(path)
                with Image.open(path) as picture:
                    rgb = picture.convert("RGB")
                    if min(rgb.size) <= IMG_SIDE and path.lower().endswith((".jpg", ".jpeg")):
                        skipped += 1
                        before += size
                        after += size
                        continue
                    rgb.thumbnail((IMG_SIDE, IMG_SIDE), Image.LANCZOS)
                    target = os.path.splitext(path)[0] + ".jpg"
                    rgb.save(target, quality=JPEG_QUALITY)
                new_size = os.path.getsize(target)
                if os.path.abspath(target) != os.path.abspath(path):
                    os.remove(path)
                before += size
                after += new_size
                files += 1
            except Exception as error:
                print(f"   SKIP {path}: {type(error).__name__}: {error}")
    return files, skipped, before, after


def main():
    roots = sys.argv[1:] or ["ai-services/classifier/data/multi"]
    files = skipped = before = after = 0
    for root in roots:
        if not os.path.isdir(root):
            print(f"not a folder: {root}")
            continue
        f, s, b, a = shrink(root)
        files += f
        skipped += s
        before += b
        after += a
    gb = 1024 ** 3
    print(f"rewritten      {files}")
    print(f"already <=256  {skipped}")
    print(f"before         {before / gb:.2f} GB")
    print(f"after          {after / gb:.2f} GB")
    print(f"saved          {(before - after) / gb:.2f} GB")


if __name__ == "__main__":
    main()
