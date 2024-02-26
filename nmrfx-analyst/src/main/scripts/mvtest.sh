#/bin/sh

cd ../../nmrfx-test-data-gen
files=`ls`
for file in $files
do
  newFile=$(echo $file| cut -c 5-)
  cp -r -p $file ../nmrfx-test-data/valid/$newFile
done
