import re, glob, os
os.chdir(r"D:\demo\lxmusic-android")
rows=[]
total=0
for f in sorted(glob.glob("app/build/test-results/testDebugUnitTest/*.xml")):
    s=open(f,encoding="utf-8",errors="replace").read()
    n=len(re.findall(r"<testcase ",s))
    name=re.search(r'name="([^"]+)"',s)
    total+=n
    rows.append((name.group(1) if name else f, n))
for r in sorted(rows,key=lambda x:-x[1]):
    print(r[1], r[0])
print("TOTAL", total, "classes", len(rows))
