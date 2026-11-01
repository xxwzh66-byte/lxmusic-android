# -*- coding: utf-8 -*-
# JUnit XML 结果计数：python _cnt.py [results_dir]
# 默认 app/build/test-results/testDebugUnitTest（相对仓库根）。
import glob
import os
import sys
import xml.etree.ElementTree as ET

root = os.path.dirname(os.path.abspath(__file__))
d = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
    root, "app", "build", "test-results", "testDebugUnitTest")
files = sorted(glob.glob(os.path.join(d, "TEST-*.xml")))
tot = fail = err = 0
for p in files:
    r = ET.parse(p).getroot()
    tot += int(r.get("tests"))
    fail += int(r.get("failures"))
    err += int(r.get("errors"))
print("files=%d tests=%d failures=%d errors=%d" % (len(files), tot, fail, err))
