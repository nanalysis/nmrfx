with open('target/myFilter.properties','r') as f1:
    props = f1.read()
    f1.close()
props = props.replace('./','')
elems = props.split(':')
firstElem = elems[0].replace('classpath=','')
with open('target/Manifest.txt','w') as f1:
    f1.write('Class-Path:')
    f1.write(" "+firstElem+"\n")
    for elem in elems[1:]:
        f1.write("  "+elem+"\n")
    f1.write("\n")


