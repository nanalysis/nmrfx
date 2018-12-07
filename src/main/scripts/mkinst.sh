#!/bin/bash

for os in "macosx-amd64" "linux-amd64" "windows-amd64"
do
    jversion=192
    tail=""
    if [[ $os == "linux-amd64" ]]
    then
        jversion=152
    fi
    if [[ $os == "macosx-amd64" ]]
    then
        tail="_unpacked"
    fi
    version=1.8.0_${jversion}

    jreFileName=${os}-$version$tail".tar.gz"
    echo $jreFileName

    dir=installers/$os
    if [ -e $dir ]
    then
         rm -rf $dir
    fi

    mkdir -p $dir
    cd $dir
    cp -r -p ../../target/processorgui-*-bin/proc* .
    sdir=`ls -d processorgui-*`
    cd $sdir
    cp -p ../../../../nmrfxprocessor/target/processor-*-bin/processor-*/nmrfxp .
    cp -p ../../../../nmrfxprocessor/target/processor-*-bin/processor-*/nmrfxp.bat .

    mkdir jre
    cd jre
    tar xzf ~/.install4j6/jres/$jreFileName
    cd ..
    cd ..
    pwd

    if [ -e "$sdir/jre/Contents/Home/jre" ]
    then
        mv $sdir/jre/Contents/Home/jre junk
        rm -rf $sdir/jre
        mv junk $sdir/jre
    fi
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
