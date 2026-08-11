#!/bin/bash

set -e # stop script when error occurs
set -u # stop when undefined variable is used
#set -x # print all execution (good for debugging)

SCRIPT_FOLDER=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )
ANDROID_TEST_RESULTS_DIR="library/build/outputs/androidTest-results/connected"

TYPE=""
CONFIG_JSON=""
CONFIG_FILE_PATH="library/src/androidTest/assets/config.json"

print_usage() {
    echo "Usage: sh scripts/test.sh -type <unit|android> [-config '<json>']"
    echo ""
    echo "  -type     unit    Runs JVM unit tests (:library:testDebugUnitTest)"
    echo "            android Runs Android instrumentation tests (:library:connectedDebugAndroidTest)"
    echo "  -config   Optional JSON content that will be written to"
    echo "            ${CONFIG_FILE_PATH}"
}

die() {
    echo "ERROR: $1"
    exit 1
}

parse_arguments() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            -type)
                [ $# -ge 2 ] || die "Missing value for -type."
                TYPE="$2"
                shift 2
                ;;
            -config)
                [ $# -ge 2 ] || die "Missing value for -config."
                CONFIG_JSON="$2"
                shift 2
                ;;
            -h|--help)
                print_usage
                exit 0
                ;;
            *)
                die "Unknown parameter ${1}"
                ;;
        esac
    done
}

validate_arguments() {
    if [ -z "${TYPE}" ]; then
        print_usage
        die "Missing required parameter: -type"
    fi

    if [ "${TYPE}" != "unit" ] && [ "${TYPE}" != "android" ]; then
        print_usage
        die "Invalid -type value '${TYPE}'. Expected 'unit' or 'android'."
    fi
}

write_android_test_config_if_provided() {
    if [ -n "${CONFIG_JSON}" ]; then
        printf '%s' "${CONFIG_JSON}" > "${CONFIG_FILE_PATH}"
        echo "Config written to ${CONFIG_FILE_PATH}"
    fi
}

select_running_emulator_serial() {
    local serials
    serials=$(adb devices | awk '/^emulator-[0-9]+[[:space:]]+device$/ { print $1 }')

    if [ -z "${serials}" ]; then
        echo "adb devices output:"
        adb devices
        die "No running Android emulator in 'device' state found via adb."
    fi

    local selected
    selected=$(printf '%s\n' "${serials}" | head -n 1)
    echo "Detected emulator serial(s):"
    printf '%s\n' "${serials}"
    echo "Using emulator serial: ${selected}"
    export ANDROID_SERIAL="${selected}"
}

collect_android_report_files() {
    local report_dir="$1"
    # AGP/Gradle can place connected-test XML reports in variant/device subfolders.
    # Return a recursive, stable list so CI parsing is not tied to one exact layout.
    find "${report_dir}" -type f -name "*.xml" | sort
}

count_report_testcases() {
    local xml="$1"
    local tests
    tests=$(grep -c '<testcase ' "${xml}" || true)
    if [ "${tests}" -eq 0 ]; then
        # Fallback for older/minimal XML formats that expose only suite attributes.
        tests=$(grep -oE 'tests="[0-9]+"' "${xml}" | head -n 1 | grep -oE '[0-9]+' || echo "0")
    fi
    echo "${tests}"
}

count_report_skipped() {
    local xml="$1"
    local skipped
    skipped=$(grep -c '<skipped' "${xml}" || true)
    if [ "${skipped}" -eq 0 ]; then
        skipped=$(grep -oE 'skipped="[0-9]+"' "${xml}" | head -n 1 | grep -oE '[0-9]+' || echo "0")
    fi
    echo "${skipped}"
}

verify_android_instrumentation_results() {
    local report_dir="${ANDROID_TEST_RESULTS_DIR}"
    local total_tests=0
    local skipped_tests=0
    local report_files_count=0

    if [ ! -d "${report_dir}" ]; then
        die "Android test report directory was not generated: ${report_dir}"
    fi

    while IFS= read -r xml; do
        local tests
        local skipped

        tests=$(count_report_testcases "${xml}")
        skipped=$(count_report_skipped "${xml}")

        total_tests=$((total_tests + tests))
        skipped_tests=$((skipped_tests + skipped))
        report_files_count=$((report_files_count + 1))
    done < <(collect_android_report_files "${report_dir}")

    if [ "${report_files_count}" -eq 0 ]; then
        die "Android test reports were not generated in ${report_dir}"
    fi

    # CI guard: fail if no tests ran or if every discovered test case was skipped.
    echo "Android instrumentation results: total=${total_tests}, skipped=${skipped_tests}, reportFiles=${report_files_count}"
    if [ "${total_tests}" -eq 0 ] || [ "${total_tests}" -eq "${skipped_tests}" ]; then
        die "Android instrumentation tests were not executed successfully (total=${total_tests}, skipped=${skipped_tests})."
    fi
}

run_unit_tests() {
    ./gradlew :library:testDebugUnitTest
}

run_android_tests() {
    select_running_emulator_serial
    ./gradlew :library:connectedDebugAndroidTest
    verify_android_instrumentation_results
}

run_tests() {
    if [ "${TYPE}" == "unit" ]; then
        run_unit_tests
    else
        run_android_tests
    fi
}

main() {
    parse_arguments "$@"
    validate_arguments

    trap 'popd >/dev/null' EXIT
    pushd "${SCRIPT_FOLDER}/.." >/dev/null
    write_android_test_config_if_provided
    run_tests
    popd >/dev/null
    trap - EXIT
}

main "$@"
