#!/bin/bash


version="1.8.0_152"
os=macosx-amd64
tail="_unpacked"

for os in "macosx-amd64" "linux-amd64"
do
    fileName=${os}-$version$tail".tar.gz"
    echo $fileName

    dir=installers/$os
    if [ -e $dir ]
    then
         rm -rf $dir
    fi

    mkdir -p $dir
    cd $dir
    cp -r -p ../../target/datachordgui-*-bin/datach* .
    sdir=`ls -d datachordgui-*`
    cd $sdir

    mkdir jre
    cd jre
    tar xzf ~/.install4j6/jres/$fileName
    cd ..
    cd ..
    pwd

    if [ -e "$sdir/jre/Contents/Home/jre" ]
    then
        mv $sdir/jre/Contents/Home/jre junk
        rm -rf $sdir/jre
        mv junk $sdir/jre
    fi
    tail=""
    fname=`echo $sdir | tr '.' '_'`
    if [[ $os == "linux-amd64" ]]
    then
        tar czvf ${fname}_${os}.tar.gz $sdir
    elif [[ $os == "macosx-amd64" ]]
    then
        tar czvf ${fname}_${os}.tar.gz $sdir
    else
        zip -r ${fname}_${os}.zip $sdir
    fi
    cd ../..
done
