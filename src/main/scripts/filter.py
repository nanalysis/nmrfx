with open('target/myFilterBat.properties','r') as f1:
    props = f1.read()
    f1.close()
elems = props.split(';')
firstElem = elems[0].replace('classpath','wclasspath')
with open('target/myFilterBat.properties','w') as f1:
    f1.write('wwwclasspath=\\n\\\n')
    f1.write("set "+firstElem+";\\n\\\n")
    for elem in elems[1:]:
        f1.write("set wclasspath=%wclasspath%"+elem+";\\n\\\n")
    #f1.write('"')
