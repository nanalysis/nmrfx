#!/bin/sh
version=0.40
name=fat

pom="<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">
    <modelVersion>4.0.0</modelVersion>
    <groupId>org.nmrfx</groupId>
    <artifactId>nmrfx-ejml</artifactId>
    <version>11.1.2</version>
    <dependencies>
        <dependency>
            <groupId>org.ejml</groupId>
            <artifactId>ejml-core</artifactId>
            <version>0.40</version>
        </dependency>
        <dependency>
            <groupId>org.ejml</groupId>
            <artifactId>ejml-simple</artifactId>
            <version>0.40</version>
        </dependency>
        <dependency>
            <groupId>org.ejml</groupId>
            <artifactId>ejml-ddense</artifactId>
            <version>0.40</version>
        </dependency>
        <dependency>
            <groupId>org.ejml</groupId>
            <artifactId>ejml-zdense</artifactId>
            <version>0.40</version>
        </dependency>
    </dependencies>
</project>
"

echo $pom > pom.xml

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
