#!/usr/bin/env python3
"""Packaging helpers for the Icon Workshop build.

Usage:
    python3 scripts/app_pack.py jar    # zip build/classes into build/classes.jar
    python3 scripts/app_pack.py pack   # add classes.dex + app/assets/** to the APK
"""
import os
import shutil
import sys
import zipfile

mode = sys.argv[1] if len(sys.argv) > 1 else ""

if mode == "jar":
    base = "build/classes"
    out = "build/classes.jar"
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        for root, _, files in os.walk(base):
            for name in files:
                if name.endswith(".class"):
                    path = os.path.join(root, name)
                    z.write(path, os.path.relpath(path, base).replace(os.sep, "/"))
    print("classes.jar:", len(zipfile.ZipFile(out).namelist()), "entries")

elif mode == "pack":
    shutil.copy("build/base.apk", "build/app-unsigned.apk")
    entries = [("build/dex/classes.dex", "classes.dex")]

    assets = "app/assets"
    for root, _, files in os.walk(assets):
        for name in files:
            path = os.path.join(root, name)
            rel = os.path.relpath(path, assets).replace(os.sep, "/")
            entries.append((path, "assets/" + rel))

    packed = {dst for _, dst in entries}
    required = {"assets/entry-template.apk", "assets/niji.p12"}
    missing = required - packed
    if missing:
        sys.exit("missing required assets: " + ", ".join(sorted(missing)))

    with zipfile.ZipFile("build/app-unsigned.apk", "a", zipfile.ZIP_DEFLATED) as z:
        for src, dst in entries:
            z.write(src, dst)
    print("packed:", len(zipfile.ZipFile("build/app-unsigned.apk").namelist()), "entries")

else:
    sys.exit("usage: app_pack.py jar|pack")
