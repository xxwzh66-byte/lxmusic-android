# -*- coding: utf-8 -*-
# 逐类测试计数：python _per_class.py [results_dir]
import glob
import os
import re
import sys

root = os.path.dirname(os.path.abspath(__file__))
d = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
    root, "app", "build", "test-results", "testDebugUnitTest")
rows = []
total = 0
for f in sorted(glob.glob(os.path.join(d, "*.xml"))):
    s = open(f, encoding="utf-8", errors="replace").read()
    n = len(re.findall(r"<testcase ", s))
    name = re.search(r'name="([^"]+)"', s)
    total += n
    rows.append((name.group(1) if name else f, n))
for r in sorted(rows, key=lambda x: -x[1]):
    print(r[1], r[0])
print("TOTAL", total, "classes", len(rows))
