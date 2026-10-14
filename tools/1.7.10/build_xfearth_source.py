#!/usr/bin/env python3
"""Build the external earth2000 raster package consumed by XenoFactions."""
import argparse, hashlib, json, shutil, struct
from pathlib import Path

FILES = ("HeightMap20k.png", "BiomeMap20k.png", "WaterMap20k.png", "Ice20k.png", "globecover20k.png")
WIDTH, HEIGHT = 21504, 10752

def dimensions(path):
    with path.open("rb") as stream:
        header = stream.read(24)
    if len(header) != 24 or header[:8] != b"\x89PNG\r\n\x1a\n" or header[12:16] != b"IHDR":
        raise ValueError("{} is not a PNG".format(path))
    return struct.unpack(">II", header[16:24])

def digest(path):
    result = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            result.update(block)
    return result.hexdigest()

def build(images, output):
    if output.exists() and any(output.iterdir()):
        raise ValueError("output must be absent or empty: {}".format(output))
    sources = []
    for name in FILES:
        path = images / name
        if not path.is_file():
            raise ValueError("missing required source raster: {}".format(path))
        actual = dimensions(path)
        if actual != (WIDTH, HEIGHT):
            raise ValueError("{} must be {}x{}, got {}x{}".format(name, WIDTH, HEIGHT, *actual))
        sources.append((name, path, digest(path)))
    output.mkdir(parents=True, exist_ok=True)
    for name, path, _ in sources:
        shutil.copy2(str(path), str(output / name))
    manifest = {
        "formatVersion": 1, "sourceId": "earth2000", "profile": "earth2000",
        "minecraftVersion": "1.7.10", "effectiveScale": 2000,
        "width": WIDTH, "height": HEIGHT, "minimumX": -10752,
        "maximumX": 10751, "minimumZ": -5376, "maximumZ": 5375,
        "seaLevel": 62, "generationMode": "RASTER_PREGEN", "generatorVersion": "1",
        "rasters": {name: sha for name, _, sha in sources},
    }
    (output / "xenoearth-source.json").write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")

def build_map_tiles(raster, output, map_id):
    """Offline-only display pyramid, preserving the terrain raster's exact extent."""
    from PIL import Image
    import re
    if not re.fullmatch(r"[a-z0-9][a-z0-9_-]{0,63}", map_id):
        raise ValueError("invalid map image ID")
    if output.exists() and any(output.iterdir()):
        raise ValueError("output must be absent or empty: {}".format(output))
    # These are trusted, explicitly selected local terrain inputs, not gameplay downloads.
    Image.MAX_IMAGE_PIXELS = None
    with Image.open(raster) as source:
        width, height = source.size
        if width <= 0 or height <= 0:
            raise ValueError("empty terrain raster")
        output.mkdir(parents=True, exist_ok=True)
        levels = []
        for level, longest in enumerate((512, 1024, 2048)):
            ratio = min(1.0, longest / max(width, height))
            w, h = max(1, round(width * ratio)), max(1, round(height * ratio))
            image = source.resize((w, h), Image.Resampling.NEAREST).convert("RGB")
            folder = output / str(level)
            folder.mkdir()
            for z in range((h + 255) // 256):
                for x in range((w + 255) // 256):
                    image.crop((x * 256, z * 256, min(w, (x + 1) * 256), min(h, (z + 1) * 256))).save(folder / ("{}-{}.png".format(x, z)))
            levels.append({"width": w, "height": h})
            image.close()
        manifest = {"formatVersion": 1, "id": map_id, "sourceFile": raster.name,
                    "sourceSha256": digest(raster), "sourceWidth": width, "sourceHeight": height,
                    "orientation": "x-east-z-south", "tileSize": 256, "levels": levels}
        (output / "map-image.json").write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--profile", choices=("earth2000",))
    parser.add_argument("--images", type=Path, default=Path("images"))
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--map-tiles-only", action="store_true")
    parser.add_argument("--map-raster", type=Path)
    parser.add_argument("--map-id")
    args = parser.parse_args()
    try:
        if args.map_tiles_only:
            if not args.map_raster or not args.map_id:
                parser.error("--map-tiles-only requires --map-raster and --map-id")
            build_map_tiles(args.map_raster, args.output, args.map_id)
        else:
            if not args.profile:
                parser.error("--profile is required for a terrain source package")
            build(args.images, args.output)
    except ValueError as error:
        parser.error(str(error))

if __name__ == "__main__":
    main()
