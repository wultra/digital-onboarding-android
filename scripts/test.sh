#!/bin/bash

set -e # stop script when error occurs
set -u # stop when undefined variable is used
#set -x # print all execution (good for debugging)

SCRIPT_FOLDER=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )

TYPE=""
CONFIG_JSON=""

print_usage() {
    echo "Usage: sh scripts/test.sh -type <unit|android> [-config '<json>']"
    echo ""
    echo "  -type     unit    Runs JVM unit tests (:library:testDebugUnitTest)"
    echo "            android Runs Android instrumentation tests (:library:connectedDebugAndroidTest)"
    echo "  -config   Optional JSON content that will be written to"
    echo "            library/src/androidTest/assets/config.json"
}

# Parse parameters of this script
while [[ $# -gt 0 ]]
do
    case "$1" in
        -type)
            TYPE="$2"
            shift
            shift
            ;;
        -config)
            CONFIG_JSON="$2"
            shift
            shift
            ;;
        -h|--help)
            print_usage
            exit 0
            ;;
        *)
            echo "Unknown parameter ${1}"
            print_usage
            exit 1
            ;;
    esac
done

if [ -z "${TYPE}" ]; then
    echo "Missing required parameter: -type"
    print_usage
    exit 1
fi

pushd "${SCRIPT_FOLDER}/.."

if [ -n "${CONFIG_JSON}" ]; then
    printf '%s' "${CONFIG_JSON}" > "library/src/androidTest/assets/config.json"
    echo "Config written to library/src/androidTest/assets/config.json"
fi

if [ "${TYPE}" == "unit" ] ; then
    ./gradlew :library:testDebugUnitTest
elif [ "${TYPE}" == "android" ] ; then
    ./gradlew :library:connectedDebugAndroidTest
else
    echo "Invalid -type value '${TYPE}'. Expected 'unit' or 'android'."
    print_usage
    exit 1
fi

popd
