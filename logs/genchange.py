import re
import time
pat=re.compile("^[0-9]+ ")

print '''---
title: NMRFx Processor Changes
onpage_menu: false

---
## NMRFx Processor ChangeLog
'''
cNew = '<span style="color:white;background-color:blue">'
cBug = '<span style="color:white;background-color:red">'
cImp = '<span style="color:black;background-color:yellow">'

cS={"IMPROVE":cImp,"BUG":cBug,"NEW":cNew}

cE = '</span>'
with open('logall.txt','r') as f1:
    for line in f1:
        line = line.strip()
        if len(line) > 0:
            if pat.match(line):
                fields = line.split()
                updateTime = int(fields[0])
                version = fields[-1]
                f = time.strftime(" %d %b %Y", time.gmtime(updateTime))
                print '\n\n### Version '+version+' Released'+ f 
            else:
                fields = line.split('\t')
                print '+ **'+cS[fields[0]] + fields[0]+'  '+fields[1].upper()+cE+'**     '+fields[2] + '\n'
