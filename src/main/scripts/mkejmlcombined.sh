#!/bin/sh
version=0.40
name=fat
mvn dependency:go-offline
dir=${HOME}/.m2/repository/org/ejml
rm -rf ejmlcombine
mkdir ejmlcombine
cd ejmlcombine
for fileType in core simple ddense zdense
do
echo $fileType
    file=${dir}/ejml-${fileType}/${version}/ejml-${fileType}-${version}.jar
    jar xvf $file
done
jar cf ../ejml-${name}-${version}.jar *
cd ..
mvn install:install-file  -Dfile=ejml-${name}-0.40.jar -DgroupId=org.ejml -DartifactId=ejml-${name} -Dpackaging=jar -Dversion=0.40
