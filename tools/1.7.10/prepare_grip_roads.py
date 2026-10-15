#!/usr/bin/env python3
"""Offline GRIP4 vector -> final-grid categorical PNG. No Minecraft or OSM input."""
import argparse
import hashlib
import json
import math
import sqlite3
import struct
import sys
import tempfile
import zipfile
from contextlib import closing
from pathlib import Path

VERSION = 1
SOURCE_URL = "https://dataportaal.pbl.nl/data/GRIP4/GRIP4_global_vector_fgdb.zip"
ATTRIBUTION = "GRIP4: Meijer, Huijbregts, Schotten and Schipper (2018), Global patterns of current and future road infrastructure, Environmental Research Letters 13-064006; GLOBIO / PBL"
LIMITATIONS = "Generalized historical source routes; coverage and junction topology may be incomplete. Not suitable for navigation. Symbolic widths, no inferred connections, OSM IDs, grades, bridges or tunnels. GRIP classification does not establish bridge/tunnel elevation; water crossings are excluded during WorldPainter application, but dry elevated/underground segments without source flags cannot be identified. Display does not establish road blocks in a world."
# Identity deliberately covers the coordinate/terrain contract, not incidental export diagnostics.
PROFILE_DEFAULTS = {"minimumY": 0, "maximumY": 255, "heightImplementation": "vanilla-anvil", "heightImplementationVersion": "1.7.10", "verticalElevationScale": 1.0}
PROFILE_FIELDS = "formatVersion profile targetMinecraftVersion sourceScale resize effectiveScale width height minimumX maximumX minimumZ maximumZ minimumSurfaceY maximumSurfaceY seaLevel projection populationMode caves ores lava structures vegetation minimumY maximumY heightImplementation heightImplementationVersion verticalElevationScale".split()


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=True)


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def load_profile(path):
    if zipfile.is_zipfile(path):
        with zipfile.ZipFile(path) as archive:
            entry = archive.getinfo("xenoearth-profile.json")
            if entry.file_size > 65536:
                raise ValueError("Pack profile exceeds 64 KiB")
            return json.loads(archive.read(entry))
    return json.loads(path.read_text(encoding="utf-8-sig"))


def profile_contract(profile):
    p = {key: profile.get(key, PROFILE_DEFAULTS.get(key)) for key in PROFILE_FIELDS}
    if any(value is None for value in p.values()):
        raise ValueError("Use a generated/installed full xenoearth-profile.json, not the old root scale-only contract")
    w, h = p["width"], p["height"]
    if (p["formatVersion"] != 1 or p["targetMinecraftVersion"] != "1.7.10" or p["projection"] != "equirectangular"
            or p["effectiveScale"] <= 0 or w <= 0 or h <= 0 or w % 16 or h % 16
            or w * h > 64 * 1024 * 1024 or p["maximumX"] - p["minimumX"] + 1 != w
            or p["maximumZ"] - p["minimumZ"] + 1 != h
            or w * p["effectiveScale"] / 120000 > 360 or h * p["effectiveScale"] / 120000 > 180):
        raise ValueError("Unsupported profile geometry (maximum mask: 64 Mi pixels)")
    return p


def source_path(source):
    if source.is_dir() and source.suffix.lower() == ".gdb":
        return str(source.resolve())
    if not zipfile.is_zipfile(source):
        raise ValueError("Source must be an extracted .gdb or a complete GRIP ZIP")
    with zipfile.ZipFile(source) as archive:
        roots = sorted({name[:name.lower().index(".gdb/") + 4] for name in archive.namelist() if ".gdb/" in name.lower()})
        if len(roots) != 1 or any(".." in Path(root).parts for root in roots):
            raise ValueError("Expected exactly one geodatabase in the archive")
        print("Checking ZIP CRCs (one sequential pass)...", flush=True)
        bad = archive.testzip()
        if bad:
            raise ValueError("Incomplete/corrupt ZIP member: " + bad)
    # GDAL's virtual filesystem reads the local archive; no second extraction/download.
    return "/vsizip/" + source.resolve().as_posix() + "/" + roots[0]


def lines(geometry, ogr):
    if geometry is None:
        return
    kind = ogr.GT_Flatten(geometry.GetGeometryType())
    if kind == ogr.wkbLineString:
        yield [(geometry.GetX(i), geometry.GetY(i)) for i in range(geometry.GetPointCount())]
    elif kind == ogr.wkbMultiLineString:
        for i in range(geometry.GetGeometryCount()):
            yield from lines(geometry.GetGeometryRef(i), ogr)
    else:
        raise ValueError("GRIP route layer contains non-line geometry")


def key(point):
    return struct.pack("!dd", *(0.0 if value == 0 else value for value in point))


def antimeridian(points):
    """Split at +/-180, interpolating only the supplied segment, never joining across Earth."""
    if not points:
        return
    part = [points[0]]
    for lon, lat in points[1:]:
        a, b = part[-1]
        delta = lon - a
        if abs(delta) > 180:
            unwrapped = lon - math.copysign(360, delta)
            if unwrapped == a:
                yield part + [(a, lat)]
                part = [(lon, lat)]
                continue
            edge = 180.0 if unwrapped > a else -180.0
            y = b + (lat - b) * (edge - a) / (unwrapped - a)
            yield part + [(edge, y)]
            part = [(-edge, y), (lon, lat)]
        else:
            part.append((lon, lat))
    if len(part) > 1:
        yield part


def prepare(args):
    from osgeo import gdal, ogr, osr
    from PIL import Image, ImageDraw
    import numpy as np
    from shapely.geometry import LineString, box
    gdal.UseExceptions()
    ds = ogr.Open(source_path(args.source))
    if ds is None:
        raise ValueError("GDAL cannot open GRIP; install conda-forge gdal with OpenFileGDB")
    names = [ds.GetLayerByIndex(i).GetName() for i in range(ds.GetLayerCount())]
    print("Actual source layers:", ", ".join(names), flush=True)
    layers = []
    for name in names:
        layer = ds.GetLayerByName(name)
        definition = layer.GetLayerDefn()
        fields = [definition.GetFieldDefn(i).GetName() for i in range(definition.GetFieldCount())]
        srs = layer.GetSpatialRef()
        print(name, "fields:", fields, "CRS:", srs.ExportToWkt() if srs else "MISSING", flush=True)
        if args.layer and name not in args.layer:
            continue
        # Published GRIP4 GP_RTP: 1 highway, 2 primary, 3 secondary, 4 tertiary, 5 local.
        category = next((f for f in fields if f.upper() == "GP_RTP"), None)
        if category is None:
            continue
        if srs is None:
            raise ValueError("Missing CRS: " + name)
        srs = srs.Clone()
        srs.SetAxisMappingStrategy(osr.OAMS_TRADITIONAL_GIS_ORDER)
        target = osr.SpatialReference()
        target.ImportFromEPSG(4326)
        target.SetAxisMappingStrategy(osr.OAMS_TRADITIONAL_GIS_ORDER)
        layers.append((layer, category, osr.CoordinateTransformation(srs, target), fields, srs.ExportToWkt()))
    if args.inspect:
        return
    if not layers or (args.layer and set(args.layer) - {item[0].GetName() for item in layers}):
        raise ValueError("No requested GRIP4 GP_RTP route layers; inspect the source, do not guess names")
    if not args.profile or not args.output:
        raise ValueError("--profile and --output are required for preparation")
    p = profile_contract(load_profile(args.profile))
    if args.output.exists():
        raise ValueError("Output exists; choose a new directory (prepared masks are reusable)")
    selected = [1, 2, 3] if args.secondary else [1, 2]
    metadata = []
    for layer, field, _, fields, crs in layers:
        condition = '"' + field + '" IN (' + ','.join(map(str, selected)) + ')'
        if args.exclude_where:
            condition += " AND NOT (" + args.exclude_where + ")"
        if layer.SetAttributeFilter(condition) != 0:
            raise ValueError("Invalid source filter for " + layer.GetName())
        metadata.append({"name": layer.GetName(), "categoryField": field, "fields": fields, "crs": crs, "metadata": layer.GetMetadata()})

    def features():
        for layer, field, transform, _, _ in layers:
            layer.ResetReading()
            for feature in layer:
                category = int(feature.GetField(field))
                geometry = feature.GetGeometryRef()
                if geometry is None:
                    continue
                geometry = geometry.Clone()
                if geometry.Transform(transform) != 0:
                    raise ValueError("Failed source coordinate transformation")
                for points in lines(geometry, ogr):
                    if any(not math.isfinite(x) or not math.isfinite(y) or abs(x) > 180.000001 or abs(y) > 90.000001 for x, y in points):
                        raise ValueError("Invalid WGS84 route coordinate")
                    yield category, points

    args.output.parent.mkdir(parents=True, exist_ok=True)
    # Disk-backed shared-vertex registry bounds RAM and preserves supplied junction vertices,
    # including a vertex in the middle of one feature meeting another feature's endpoint.
    with tempfile.TemporaryDirectory(prefix="xf-grip-", dir=args.output.parent) as temporary, \
            closing(sqlite3.connect(str(Path(temporary) / "junctions.sqlite"))) as database:
        database.execute("PRAGMA cache_size=-8192")
        database.execute("CREATE TABLE vertex (point BLOB PRIMARY KEY, uses INTEGER) WITHOUT ROWID")
        count = 0
        for _, points in features():
            database.executemany("INSERT INTO vertex VALUES (?,1) ON CONFLICT(point) DO UPDATE SET uses=2", ((key(v),) for v in set(points)))
            count += 1
            if count % 100000 == 0:
                database.commit()
                print("Indexed route parts:", count, flush=True)
        database.commit()
        if count == 0:
            raise ValueError("No selected source routes; refusing an apparently successful empty mask")
        print("Preserving shared source vertices; rasterizing", count, "parts...", flush=True)
        w, h = p["width"], p["height"]
        masks = {c: Image.new("L", (w, h)) for c in selected}
        draws = {c: ImageDraw.Draw(masks[c]) for c in selected}
        bounds = box(0, 0, w - 1e-7, h - 1e-7)
        scale = 120000.0 / p["effectiveScale"]
        def paint(points, category):
            for part in antimeridian(points):
                # XFEarthProfile.blockX(lon)-minimumX / blockZ(lat)-minimumZ.
                projected = [(w / 2.0 + x * scale, h / 2.0 - y * scale) for x, y in part]
                # Simplification is AFTER projection. Endpoints/shared vertices split parts first.
                shape = LineString(projected).simplify(args.tolerance, preserve_topology=False).intersection(bounds)
                segments = [shape] if shape.geom_type == "LineString" else getattr(shape, "geoms", [])
                for segment in segments:
                    if segment.geom_type == "LineString" and not segment.is_empty:
                        draws[category].line([(math.floor(x), math.floor(z)) for x, z in segment.coords], fill=4-category, width=args.width)
        for n, (category, points) in enumerate(features(), 1):
            start = 0
            for i in range(1, len(points)-1):
                if database.execute("SELECT uses FROM vertex WHERE point=?", (key(points[i]),)).fetchone()[0] > 1:
                    paint(points[start:i+1], category)
                    start = i
            if len(points[start:]) > 1:
                paint(points[start:], category)
            if n % 100000 == 0:
                print("Rasterized route parts:", n, flush=True)
        result = np.zeros((h, w), dtype=np.uint8)
        for mask in masks.values():
            np.maximum(result, np.asarray(mask), out=result)
        staged = Path(temporary) / "prepared"
        staged.mkdir()
        Image.fromarray(result).save(staged / "roads.png", compress_level=9)
        levels = []
        for longest in (512, 1024):
            factor = min(1, longest / max(w, h))
            tw, th = max(1, round(w * factor)), max(1, round(h * factor))
            if levels and levels[-1] == {"width": tw, "height": th}:
                continue
            # Maximum preserves thin roads at overview zoom; category priority is unchanged.
            reduced = np.maximum.reduceat(result, np.arange(th) * h // th, axis=0)
            reduced = np.maximum.reduceat(reduced, np.arange(tw) * w // tw, axis=1)
            palette = np.array([[0, 0, 0, 0], [220, 173, 78, 230], [255, 199, 64, 240], [255, 227, 110, 255]], dtype=np.uint8)
            overview = Image.fromarray(palette[reduced], "RGBA")
            directory = staged / str(len(levels))
            directory.mkdir()
            for z in range(0, th, 256):
                for x in range(0, tw, 256):
                    overview.crop((x, z, min(tw, x+256), min(th, z+256))).save(directory / (str(x//256) + "-" + str(z//256) + ".png"))
            levels.append({"width": tw, "height": th})
        source_files = [args.source] if args.source.is_file() else sorted(x for x in args.source.rglob("*") if x.is_file() and not x.name.endswith(".lock"))
        source_digest = hashlib.sha256()
        archive_checksum = None
        for source_file in source_files:
            relative = source_file.name if args.source.is_file() else source_file.relative_to(args.source).as_posix()
            checksum = sha256(source_file)
            if args.source.is_file():
                archive_checksum = checksum
            source_digest.update((relative + "\0" + checksum + "\n").encode())
        manifest = {"formatVersion": 1, "processingVersion": VERSION, "source": "GRIP4", "sourceUrl": SOURCE_URL,
                    "sourceIdentity": source_digest.hexdigest(), "sourceFile": args.source.name,
                    "sourceArchiveSha256": archive_checksum,
                    "layers": metadata, "sourceMetadata": ds.GetMetadata(), "attribution": ATTRIBUTION, "license": "CC0-1.0",
                    "licenseUrl": "https://creativecommons.org/publicdomain/zero/1.0/", "limitations": LIMITATIONS,
                    "profile": p, "profileIdentity": hashlib.sha256(canonical(p).encode()).hexdigest(),
                    "width": w, "height": h, "orientation": "x-east-z-south", "pixelsPerColumn": 1,
                    "id": "grip-" + p["profile"], "sourceWidth": w, "sourceHeight": h, "tileSize": 256, "levels": levels,
                    "categories": selected, "values": {"0": "absent/unknown", "3": "highway", "2": "primary", "1": "secondary"},
                    "priority": "highway > primary > secondary", "widthBlocks": args.width, "widthPolicy": "constant-symbolic",
                    "simplificationPixels": args.tolerance, "maskSha256": sha256(staged / "roads.png"), "routeParts": count,
                    "excludeWhere": args.exclude_where, "gdalVersion": gdal.VersionInfo("RELEASE_NAME")}
        manifest["tileHashes"] = {file.relative_to(staged).as_posix(): sha256(file) for file in sorted(staged.glob("*/*.png"))}
        text = json.dumps(manifest, indent=2, sort_keys=True) + "\n"
        if len(text.encode("utf-8")) > 65536:
            raise ValueError("Source metadata exceeds the 64 KiB road manifest limit; select the relevant route layers explicitly")
        (staged / "roads.json").write_text(text, encoding="utf-8")
        staged.rename(args.output)
    print("Prepared", args.output, "(copy roads.json and roads.png together)")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--profile", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--inspect", action="store_true", help="print actual layers, fields and CRS without conversion")
    parser.add_argument("--layer", action="append", help="explicit layer(s); default: all actual GP_RTP layers")
    parser.add_argument("--exclude-where", help="optional OGR SQL predicate for source-supplied unsupported structures; use only verified fields/values from that input")
    parser.add_argument("--secondary", action="store_true")
    parser.add_argument("--width", type=int, default=1, help="odd symbolic block width, 1..31")
    parser.add_argument("--tolerance", type=float, default=0.5, help="map pixels; 0 disables simplification")
    args = parser.parse_args()
    if args.width not in range(1, 32, 2) or not math.isfinite(args.tolerance) or args.tolerance < 0:
        parser.error("width must be odd 1..31; tolerance must be finite and nonnegative")
    try:
        prepare(args)
    except (ValueError, RuntimeError, OSError, ImportError) as error:
        parser.exit(1, "GRIP preparation failed: " + str(error) + "\n")


if __name__ == "__main__":
    main()
