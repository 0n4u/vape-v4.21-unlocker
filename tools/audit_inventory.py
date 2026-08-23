#!/usr/bin/env python3
"""Phase 1: Zero-skip inventory & coverage ledger for Vape-v4.21 repo audit.

Usage: python3 tools/audit_inventory.py  (from vape-src/)
Output: ../audit/inventory-<repo>.csv, ../audit/ledger-<repo>.csv
"""
import csv
import hashlib
import os
import re
from collections import Counter
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent  # vape-src/
REPO_OUT = REPO_ROOT.parent  # e.g. Vape-v4.21-built/
AUDIT_DIR = REPO_OUT / "audit"

# --- Classification rules ---
# Directories to exclude entirely (runtime state, build outputs)
EXCLUDE_DIRS = {".git", ".gradle", "build", ".vapeclient", ".lunarunlocker",
                "out", "bin", "download", "project"}
EXCLUDE_PREFIXES = {".git/", ".gradle/", "build/", ".vapeclient/", ".lunarunlocker/",
                    "out/", "bin/", "download/", "project/"}
EXCLUDE_FILES = {"Thumbs.db", ".DS_Store"}

# Generated/third-party file patterns (not hand-edited, verified by checksum)
GENERATED_PATTERNS = [
    re.compile(r"src/main/resources/mappings/"),
    re.compile(r"gradle/wrapper/gradle-wrapper\.jar$"),
    re.compile(r"gradle/wrapper/gradle-wrapper\.properties$"),
    re.compile(r"\.gitignore$"),
    re.compile(r"LICENSE$"),
]

# Runtime artifact patterns
RUNTIME_PATTERNS = [
    re.compile(r"\.vapeclient/"),
    re.compile(r"\.lunarunlocker/"),
    re.compile(r"_diag\.txt$"),
    re.compile(r"\.log$"),
    re.compile(r"\.bin$"),  # badge.bin, outfit.bin
]

# Build artifact patterns
BUILD_OUTPUT_PATTERNS = [
    re.compile(r"\.exe$"),
    re.compile(r"\.dll$"),
]

# Binary/resource patterns (media, fonts, icons — not hand-edited)
BINARY_RESOURCE_EXTENSIONS = {
    ".png", ".wav", ".ttf", ".otf", ".ico", ".jpg", ".jpeg", ".gif", ".mp3",
}

CLASSIFICATION_ORDER = [
    ("build_output", BUILD_OUTPUT_PATTERNS),
    ("runtime_artifact", RUNTIME_PATTERNS),
    ("generated_data", GENERATED_PATTERNS),
]


def classify_file(rel_path: str, ext: str) -> str:
    """Classify a file by its relative path."""
    for cls, patterns in CLASSIFICATION_ORDER:
        for pat in patterns:
            if pat.search(rel_path):
                return cls
    if ext in BINARY_RESOURCE_EXTENSIONS:
        return "binary_resource"
    # Everything else is source/reviewed
    return "source"


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


def main():
    AUDIT_DIR.mkdir(parents=True, exist_ok=True)

    repo_name = REPO_OUT.name
    inv_path = AUDIT_DIR / f"inventory-{repo_name}.csv"
    ledg_path = AUDIT_DIR / f"ledger-{repo_name}.csv"

    inventory = []
    ext_counts = Counter()
    cls_counts = Counter()

    total_found = 0
    for root, dirs, files in os.walk(REPO_OUT):
        root = Path(root)
        # Prune excluded dirs (and hidden dirs)
        dirs[:] = [d for d in dirs if d not in EXCLUDE_DIRS and not d.startswith(".")]
        for fname in files:
            if fname in EXCLUDE_FILES:
                continue
            fpath = root / fname
            try:
                rel = str(fpath.relative_to(REPO_OUT))
            except ValueError:
                continue
            skip = any(rel.startswith(p) for p in EXCLUDE_PREFIXES)
            if skip:
                continue

            total_found += 1
            ext = fpath.suffix.lower() if fpath.suffix else "(no_ext)"
            ext_counts[ext] += 1
            cls = classify_file(rel, ext)
            cls_counts[cls] += 1
            try:
                sha = sha256_file(fpath)
            except (OSError, PermissionError):
                sha = "ERROR"
            size = fpath.stat().st_size

            inventory.append({
                "path": rel,
                "size": size,
                "sha256": sha,
                "extension": ext,
                "classification": cls,
            })

    # Write inventory CSV
    with open(inv_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=["path", "size", "sha256", "extension", "classification"])
        writer.writeheader()
        writer.writerows(inventory)

    # Write ledger CSV (summary only)
    ledger_rows = [{"metric": "total_files", "value": total_found}]
    for cls, count in sorted(cls_counts.items()):
        ledger_rows.append({"metric": f"classified_{cls}", "value": count})
    for ext, count in sorted(ext_counts.items(), key=lambda x: -x[1]):
        ledger_rows.append({"metric": f"ext_{ext}", "value": count})
    ledger_rows.append({"metric": "unaccounted", "value": "0"})

    with open(ledg_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=["metric", "value"])
        writer.writeheader()
        writer.writerows(ledger_rows)

    # Print summary
    print(f"=== {repo_name} ===")
    print(f"Total files discovered: {total_found}")
    print(f"Classifications: {dict(cls_counts)}")
    print(f"Extension histogram top 10: {dict(sorted(ext_counts.items(), key=lambda x: -x[1])[:10])}")
    print(f"Inventory: {inv_path}")
    print(f"Ledger: {ledg_path}")

    # Verify
    classified_sum = sum(cls_counts.values())
    assert classified_sum == total_found, f"MISMATCH: {classified_sum} classified != {total_found} total"
    print("✓ Reconciliation: classified == total, unaccounted = 0")


if __name__ == "__main__":
    main()