#!/usr/bin/env python3
"""Optional, bounded GRIP acquisition. Existing complete local files are reused."""
import argparse
import json
import re
import time
import urllib.request
import zipfile
from pathlib import Path
from prepare_grip_roads import SOURCE_URL, sha256


def verify(path, checksum):
    with zipfile.ZipFile(path) as archive:
        if not archive.namelist() or archive.testzip():
            raise ValueError("ZIP CRC validation failed")
    if checksum and sha256(path) != checksum.lower():
        raise ValueError("Published SHA-256 mismatch")


def download(destination, checksum):
    if destination.exists():
        verify(destination, checksum)
        print("Reusing verified local archive:", destination)
        return
    destination.parent.mkdir(parents=True, exist_ok=True)
    partial = destination.with_name(destination.name + ".part")
    metadata = partial.with_name(partial.name + ".json")
    for attempt in range(3):
        try:
            offset = partial.stat().st_size if partial.exists() else 0
            previous = json.loads(metadata.read_text()) if metadata.exists() else {}
            if offset and previous.get("url") != SOURCE_URL:
                raise ValueError("Unidentified partial file; preserve it separately before retrying")
            if offset and offset == previous.get("size"):
                verify(partial, checksum)
                partial.rename(destination)
                metadata.unlink(missing_ok=True)
                return
            headers = {"Accept-Encoding": "identity", "User-Agent": "XF-Earth-offline/1"}
            if offset:
                headers["Range"] = "bytes=" + str(offset) + "-"
                validator = previous.get("validator")
                if validator:
                    headers["If-Range"] = validator
            print("GRIP attempt", attempt + 1, "of 3; resume byte", offset, flush=True)
            # Timeout covers connection/TLS and a stalled socket read. Slow trickles
            # additionally fail the 30-second minimum-throughput window below.
            with urllib.request.urlopen(urllib.request.Request(SOURCE_URL, headers=headers), timeout=20) as response:
                length = response.headers.get("Content-Length")
                if offset:
                    match = re.fullmatch(r"bytes (\d+)-(\d+)/(\d+)", response.headers.get("Content-Range", ""))
                    if response.status != 206 or not match or int(match[1]) != offset or int(match[2]) + 1 != int(match[3]):
                        raise ValueError("Server refused safe resume; partial retained. Download manually or move the .part and .part.json aside")
                    total = int(match[3])
                    if total != previous.get("size"):
                        raise ValueError("Source changed size; partial retained")
                else:
                    if response.status != 200 or length is None:
                        raise ValueError("Expected HTTP 200 and a known archive length")
                    total = int(length)
                validator = response.headers.get("ETag") or response.headers.get("Last-Modified")
                if offset and previous.get("validator") and validator != previous["validator"]:
                    raise ValueError("Source validator changed; refusing to append")
                metadata.write_text(json.dumps({"url": SOURCE_URL, "size": total, "validator": validator}))
                window_time = report_time = time.monotonic()
                window_bytes = done = offset
                with partial.open("ab" if offset else "wb") as stream:
                    while True:
                        block = response.read1(256 * 1024)
                        if not block:
                            break
                        stream.write(block)
                        done += len(block)
                        if done > total:
                            raise ValueError("Response exceeds declared size")
                        now = time.monotonic()
                        if now - report_time >= 5:
                            print("Downloaded %.1f / %.1f MiB (%.1f%%)" % (done/1048576, total/1048576, 100*done/total), flush=True)
                            report_time = now
                        if now - window_time >= 30:
                            if (done - window_bytes) / (now - window_time) < 1024:
                                raise TimeoutError("Transfer stalled below 1 KiB/s for 30 seconds")
                            window_time, window_bytes = now, done
                if done != total:
                    raise IOError("Incomplete HTTP body: %d / %d bytes" % (done, total))
            print("Verifying complete ZIP CRCs...", flush=True)
            verify(partial, checksum)
            partial.rename(destination)
            metadata.unlink(missing_ok=True)
            print("Ready:", destination)
            return
        except (OSError, ValueError, zipfile.BadZipFile) as error:
            print("Download failed:", error, "Partial retained:", partial, flush=True)
            if isinstance(error, ValueError) or attempt == 2:
                raise
    raise IOError("Download attempts exhausted")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--sha256", help="only supply an actual publisher-provided checksum; none is assumed")
    args = parser.parse_args()
    if args.sha256 and not re.fullmatch("[a-fA-F0-9]{64}", args.sha256):
        parser.error("Invalid SHA-256")
    try:
        download(args.output, args.sha256)
    except (OSError, ValueError, zipfile.BadZipFile) as error:
        parser.exit(1, str(error) + "\nUse a manually downloaded complete ZIP with prepare_grip_roads.py --source.\n")
