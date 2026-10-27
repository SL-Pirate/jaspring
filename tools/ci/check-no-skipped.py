"""Fail when gated tests of a class were skipped: in CI the toolchain is installed, so a skip is a regression."""

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

results, class_name = Path(sys.argv[1]), sys.argv[2]
skipped = []
for report in results.glob("*.xml"):
    suite = ET.parse(report).getroot()
    if not suite.get("name", "").endswith(class_name):
        continue
    for case in suite.iter("testcase"):
        if case.find("skipped") is not None:
            skipped.append(case.get("name"))
if skipped:
    raise SystemExit(f"{class_name}: skipped in CI: {', '.join(skipped)}")
print(f"{class_name}: no skipped tests")
