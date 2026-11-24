#!/usr/bin/env python3
"""Import only the three optional native components from a pinned official APK."""

import argparse
import hashlib
from pathlib import Path
import shutil
from zipfile import ZipFile


UPSTREAM_SHA256 = "62c6bc2064214e4546203539c5455da5ad90f701cc2ab1b62f2ff185a5a85524"
ABIS = ("arm64-v8a", "armeabi-v7a")
LIBRARIES = ("libnovaproxy.so", "libnovaxray.so", "libnovaxrayjni.so")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    args = parser.parse_args()
    with args.apk.open("rb") as apk:
        if hashlib.file_digest(apk, "sha256").hexdigest() != UPSTREAM_SHA256:
            parser.error("expected the official GitHub APK for upstream v1.35.2")
        apk.seek(0)
        destination = Path(__file__).resolve().parents[1] / "app/src/main/jniLibs"
        with ZipFile(apk) as archive:
            entries = [(abi, name, archive.getinfo(f"lib/{abi}/{name}")) for abi in ABIS for name in LIBRARIES]
            for abi, name, entry in entries:
                output = destination / abi / name
                with archive.open(entry) as source, output.open("wb") as target:
                    shutil.copyfileobj(source, target)
                print(f"Imported {abi}/{name}")


if __name__ == "__main__":
    main()
