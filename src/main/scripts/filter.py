with open('target/myFilterBat.properties','r') as f1:
    props = f1.read()
    f1.close()

props = props.replace('classpath','wclasspath')

with open('target/myFilterBat.properties','w') as f1:
    f1.write(props)

