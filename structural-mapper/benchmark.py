"""Measure parsing cost and output coverage; counts are not recognition accuracy.

Example: python benchmark.py --repeats 3 --output /tmp/mapper.json plan.png plan.pdf
"""
from __future__ import annotations

import argparse
import hashlib
import json
import statistics
import time
from pathlib import Path

from mapper.pipeline import PARSER_VERSION, MapperOptions, Pipeline, scene_to_payload
from mapper.ingest.decode import DecodeError


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("files", type=Path, nargs="+")
    parser.add_argument("--repeats", type=int, default=3)
    parser.add_argument("--pixels-per-meter", type=float)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.repeats < 1:
        parser.error("--repeats must be positive")
    pipeline = Pipeline()
    results = []
    for path in args.files:
        raw = path.read_bytes()
        runs = []
        for _ in range(args.repeats):
            start = time.perf_counter()
            try:
                scene = pipeline.run_bytes(raw, None, MapperOptions(use_ml=False, pixels_per_meter=args.pixels_per_meter))
            except (DecodeError, ValueError) as error:
                runs.append({"error": str(error)})
                break
            payload = scene_to_payload(scene)
            elapsed = time.perf_counter() - start
            runs.append({"elapsed_seconds": elapsed, "diagnostics": payload["diagnostics"],
                         "validation": payload["validation"]})
        if "error" in runs[-1]:
            results.append({"file": path.name, "error": runs[-1]["error"]})
            continue
        durations = sorted(run["elapsed_seconds"] for run in runs)
        results.append({"file": path.name, "sha256": hashlib.sha256(raw).hexdigest(),
                        "median_seconds": statistics.median(durations), "max_seconds": max(durations),
                        "runs": runs})
    args.output.write_text(json.dumps({"parser_version": PARSER_VERSION, "backend": "deterministic",
                                      "repeats": args.repeats, "files": results}, indent=2))
    for result in results:
        if "error" in result:
            print(f'{result["file"]}: error={result["error"]}')
            continue
        diagnostics = result["runs"][-1]["diagnostics"]
        print(f'{result["file"]}: median={result["median_seconds"]:.3f}s '
              f'spaces={diagnostics["spaces"]} nodes={diagnostics["nodes"]} edges={diagnostics["edges"]}')
    if any("error" in result for result in results):
        raise SystemExit(1)


if __name__ == "__main__":
    main()
