#!/bin/sh

SHAREPOINT="java -jar sharepoint-client-1.3-jar-with-dependencies.jar"
HOST="https://nanalysis.sharepoint.com"
SITE="SoftwareDev"
PARENT_PATH="Shared Documents/Builds/nmrfx"
VALID_CHARS="A-Za-z0-9\-_.\n"
# client id and secret are passed through environment

# exits on error
set -e

BRANCH=${DELETED_REF##*/}
echo "Deleted branch: ${BRANCH}"

# delete from remote
REMOTE_FOLDER=`echo "${BRANCH}" | tr "/" "_" |  tr -c ${VALID_CHARS} "-"`
REMOTE_PATH="${PARENT_PATH}/${REMOTE_FOLDER}"

$SHAREPOINT "${HOST}" "${SITE}" api "${SHAREPOINT_CLIENT_ID}" "${SHAREPOINT_CLIENT_SECRET}" delete-folder "${REMOTE_PATH}"
