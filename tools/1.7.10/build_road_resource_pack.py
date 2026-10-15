#!/usr/bin/env python3
"""Package an existing road mask for clients; no world export or download required."""
import argparse
import json
import re
import zipfile
from pathlib import Path
from build_xfmap import canonical_json, write_entry
from prepare_grip_roads import load_profile, profile_contract, sha256


def road_files(directory, profile=None):
    from PIL import Image
    manifest_path = directory / "roads.json"
    if manifest_path.stat().st_size > 65536:
        raise ValueError("Road manifest exceeds 64 KiB")
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    contract = profile_contract(manifest["profile"])
    if profile is not None and contract != profile_contract(profile):
        raise ValueError("Prepared roads do not match the pack profile")
    if (manifest["formatVersion"] != 1 or manifest["processingVersion"] != 1 or manifest["source"] != "GRIP4"
            or manifest["orientation"] != "x-east-z-south" or manifest["pixelsPerColumn"] != 1
            or manifest["width"] != contract["width"] or manifest["height"] != contract["height"]
            or not re.fullmatch("[a-z0-9][a-z0-9_-]{0,63}", contract["profile"])):
        raise ValueError("Invalid road manifest")
    mask = directory / "roads.png"
    if sha256(mask) != manifest["maskSha256"]:
        raise ValueError("Road PNG checksum mismatch")
    with Image.open(mask) as image:
        if image.size != (contract["width"], contract["height"]) or image.mode != "L":
            raise ValueError("Road mask must be final-grid 8-bit grayscale")
        if image.getextrema()[1] > 3:
            raise ValueError("Invalid categorical mask value")
    result = [(manifest_path, "roads.json"), (mask, "roads.png")]
    levels = manifest["levels"]
    if not 1 <= len(levels) <= 2:
        raise ValueError("Invalid road display levels")
    for n, level in enumerate(levels):
        w, h = level["width"], level["height"]
        if not 0 < w <= 1024 or not 0 < h <= 1024:
            raise ValueError("Invalid road tile level dimensions")
        for z in range((h+255)//256):
            for x in range((w+255)//256):
                relative = str(n) + "/" + str(x) + "-" + str(z) + ".png"
                tile = directory / relative
                if sha256(tile) != manifest["tileHashes"][relative]:
                    raise ValueError("Road display tile checksum mismatch: " + relative)
                with Image.open(tile) as image:
                    if image.size != (min(256,w-x*256), min(256,h-z*256)) or image.mode != "RGBA":
                        raise ValueError("Road display tile dimensions/format mismatch")
                result.append((tile, relative))
    return manifest, result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--roads", required=True, type=Path)
    parser.add_argument("--profile", type=Path, help="optional installed JSON or xfmap for compatibility checking")
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    try:
        manifest, files = road_files(args.roads, load_profile(args.profile) if args.profile else None)
        if args.output.exists():
            raise ValueError("Output exists; choose a new resource pack filename")
        args.output.parent.mkdir(parents=True, exist_ok=True)
        # Exclusive creation protects existing packs. Deterministic timestamps/order/content.
        with zipfile.ZipFile(args.output, "x") as archive:
            write_entry(archive, "pack.mcmeta", canonical_json({"pack": {"pack_format": 1, "description": "GRIP4 / CC0 generalized Earth source roads; world blocks unverified"}}).encode())
            root = "assets/hfr/earthmaps/roads/" + manifest["profile"]["profile"] + "/"
            for source, name in sorted(files, key=lambda item: item[1]):
                write_entry(archive, root + name, source.read_bytes())
        print("Built client resource pack:", args.output)
    except (OSError, ValueError, KeyError) as error:
        parser.exit(1, "Road packaging failed: " + str(error) + "\n")


if __name__ == "__main__":
    main()
