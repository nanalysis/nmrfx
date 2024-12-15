import re
import sys

def get_os_version():
    ver = sys.platform.lower()
    if ver.startswith('java'):
        import java.lang
        ver = java.lang.System.getProperty("os.name").lower()
    return str(ver)

with open('target/myFilter.properties','r') as f1:
    props = f1.read()
    f1.close()
if 'windows' in get_os_version():
    props = props.replace('.\\','')
    elems = re.split(r'[=;]', props)
else:
    props = props.replace('./','')
    elems = re.split(r'[=:]', props)
for elem in elems[1:]:
     if elem.startswith("nmrfx-analyst"):
         print elem
         version = elem[14:-4]
         print version
firstElem = elems[0].replace('classpath=','')
with open('target/Manifest.txt','w') as f1:
    f1.write("Implementation-Version: " +  version + "\n")
    f1.write('Class-Path:')
    f1.write(" "+firstElem+"\n")
    for elem in elems[1:]:
        f1.write("  "+elem+"\n")
    f1.write("\n")
