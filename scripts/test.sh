#!/bin/bash

set -e # stop script when error occurs
set -u # stop when undefined variable is used
#set -x # print all execution (good for debugging)

SCRIPT_FOLDER=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )
ANDROID_TEST_RESULTS_DIR="library/build/outputs/androidTest-results/connected"

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

verify_android_instrumentation_results() {
    local report_dir="${ANDROID_TEST_RESULTS_DIR}"
    local total_tests=0
    local skipped_tests=0

    if [ ! -d "${report_dir}" ]; then
        echo "ERROR: Android test report directory was not generated: ${report_dir}"
        exit 1
    fi

    # shellcheck disable=SC2012
    local report_files
    report_files=$(ls "${report_dir}"/*.xml 2>/dev/null || true)
    if [ -z "${report_files}" ]; then
        echo "ERROR: Android test reports were not generated in ${report_dir}"
        exit 1
    fi

    while IFS= read -r xml; do
        local tests
        local skipped
        tests=$(grep -oE 'tests="[0-9]+"' "${xml}" | head -n 1 | grep -oE '[0-9]+' || echo "0")
        skipped=$(grep -oE 'skipped="[0-9]+"' "${xml}" | head -n 1 | grep -oE '[0-9]+' || echo "0")
        total_tests=$((total_tests + tests))
        skipped_tests=$((skipped_tests + skipped))
    done <<< "${report_files}"

    if [ "${total_tests}" -eq 0 ] || [ "${total_tests}" -eq "${skipped_tests}" ]; then
        echo "ERROR: Android instrumentation tests were not executed successfully (total=${total_tests}, skipped=${skipped_tests})."
        exit 1
    fi
}

select_running_emulator_serial() {
    local serials
    serials=$(adb devices | awk '/^emulator-[0-9]+[[:space:]]+device$/ { print $1 }')

    if [ -z "${serials}" ]; then
        echo "ERROR: No running Android emulator in 'device' state found via adb."
        echo "adb devices output:"
        adb devices
        exit 1
    fi

    local selected
    selected=$(printf '%s\n' "${serials}" | head -n 1)
    echo "Detected emulator serial(s):"
    printf '%s\n' "${serials}"
    echo "Using emulator serial: ${selected}"
    export ANDROID_SERIAL="${selected}"
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
    select_running_emulator_serial
    ./gradlew :library:connectedDebugAndroidTest
    verify_android_instrumentation_results
else
    echo "Invalid -type value '${TYPE}'. Expected 'unit' or 'android'."
    print_usage
    exit 1
fi

popd
