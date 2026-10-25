import re, glob, xml.etree.ElementTree as ET
tot=f=e=0
files=sorted(glob.glob(r"D:\demo\lxmusic-android\app\build\test-results\testDebugUnitTest\TEST-*.xml"))
for p in files:
    r=ET.parse(p).getroot()
    tot+=int(r.get("tests")); f+=int(r.get("failures")); e+=int(r.get("errors"))
print("files=%d tests=%d failures=%d errors=%d" % (len(files),tot,f,e))
