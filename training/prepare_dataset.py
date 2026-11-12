"""Merge exported YOLO detection datasets into one class (objeto).

Only use datasets where ALL annotated classes are countable physical items.
Input: training/raw/<dataset>/images/{train,valid,val} and labels/{train,valid,val}.
No network access; preserves per-dataset image grouping.
"""
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parent
RAW = ROOT / "raw"
OUT = ROOT / "dataset"
count = 0
for dataset in sorted(RAW.iterdir()) if RAW.exists() else []:
    if not dataset.is_dir():
        continue
    for split in ("train", "valid", "val"):
        images = dataset / "images" / split
        labels = dataset / "labels" / split
        if not images.exists() or not labels.exists():
            continue
        dest_split = "val" if split in ("valid", "val") else "train"
        for image in images.rglob("*"):
            if image.suffix.lower() not in (".jpg", ".jpeg", ".png", ".webp"):
                continue
            label = labels / image.relative_to(images).with_suffix(".txt")
            if not label.exists():
                continue
            name = f"{dataset.name}_{split}_{count}"
            target_image = OUT / "images" / dest_split / (name + image.suffix.lower())
            target_label = OUT / "labels" / dest_split / (name + ".txt")
            target_image.parent.mkdir(parents=True, exist_ok=True)
            target_label.parent.mkdir(parents=True, exist_ok=True)
            lines = []
            for line in label.read_text().splitlines():
                cols = line.strip().split()
                if len(cols) != 5:
                    raise ValueError(f"Invalid YOLO detection annotation: {label}")
                values = [float(v) for v in cols[1:]]
                if not all(0 <= v <= 1 for v in values):
                    raise ValueError(f"Out-of-range coordinates: {label}")
                lines.append("0 " + " ".join(cols[1:]))
            shutil.copy2(image, target_image)
            target_label.write_text("\n".join(lines) + ("\n" if lines else ""))
            count += 1
print(f"Prepared {count} images. Review split leakage and annotation quality before training.")
