#!/bin/sh

SHAREPOINT="java -jar sharepoint-client-1.3-jar-with-dependencies.jar"
HOST="https://nanalysis.sharepoint.com"
SITE="SoftwareDev"
PARENT_PATH="Shared Documents/Builds/nmrfx"
# client id and secret are passed through environment

TODAY=`date +%Y%m%d`

# get branch name from PR head if this is a PR workflow
BRANCH=${GITHUB_HEAD_REF##*/}
if [ -z "$BRANCH" ]; then
  # otherwise, get the ref name
  BRANCH=${GITHUB_REF##*/}
fi

# exits on error
set -e

# prepare local folder, in a temp folder managed by github action to ensure it's removed at the end
LOCAL_FOLDER_NAME=`echo "archive-${TODAY}-${BRANCH}-${GITHUB_RUN_NUMBER}" | tr "/" "_"`
LOCAL_FOLDER="${RUNNER_TEMP}/${LOCAL_FOLDER_NAME}"
mkdir "${LOCAL_FOLDER}"
cp **/target/*.zip "${LOCAL_FOLDER}"
cp nmrfx-jmx-connector/target/*.jar "${LOCAL_FOLDER}"

# upload to remote, deleting first
REMOTE_FOLDER=`echo "${BRANCH}" | tr "/" "_"`
$SHAREPOINT "${HOST}" "${SITE}" api "${SHAREPOINT_CLIENT_ID}" "${SHAREPOINT_CLIENT_SECRET}" delete-folder "${PARENT_PATH}/${REMOTE_FOLDER}"
$SHAREPOINT "${HOST}" "${SITE}" api "${SHAREPOINT_CLIENT_ID}" "${SHAREPOINT_CLIENT_SECRET}" upload-folder "${LOCAL_FOLDER}" "${PARENT_PATH}" "${REMOTE_FOLDER}"

# cleanup local folder
rm -rf "${LOCAL_FOLDER}"
