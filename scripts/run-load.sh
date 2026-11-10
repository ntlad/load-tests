#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  printf 'Usage: bash scripts/run-load.sh <10|25|50>\n' >&2
  exit 2
fi

case "$1" in
  10|25|50) users="$1" ;;
  *) printf 'Choose a load profile: 10, 25 or 50 users.\n' >&2; exit 2 ;;
esac

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"

if [[ -n "${JMETER_HOME:-}" ]]; then
  jmeter_bin="$JMETER_HOME/bin/jmeter"
elif command -v jmeter >/dev/null 2>&1; then
  jmeter_bin="$(command -v jmeter)"
else
  jmeter_bin="$HOME/Downloads/apache-jmeter-5.6.3/bin/jmeter"
fi

if [[ ! -x "$jmeter_bin" ]]; then
  printf 'JMeter not found. Add it to PATH or set JMETER_HOME; see README.md.\n' >&2
  exit 1
fi

profile="profiles/${users}-users.properties"
mkdir -p results
run_dir="$(mktemp -d "results/${users}-users-$(date +%Y%m%d-%H%M%S)-XXXXXX")"
cp "$profile" "$run_dir/profile.properties"

printf '\nLoad profile: %s users\n' "$users"
cat "$profile"
printf '\nOutput directory: %s\n\n' "$run_dir"

"$jmeter_bin" -n -t jmeter/api-load-test.jmx -q "$profile" \
  -l "$run_dir/results.jtl" -j "$run_dir/jmeter.log" \
  -Jjmeter.reportgenerator.overall_granularity=5000 \
  -e -o "$run_dir/html-report" 2>&1 | tee "$run_dir/console.txt"

if [[ ! -s "$run_dir/results.jtl" || ! -f "$run_dir/html-report/index.html" ]]; then
  printf '\nResults or HTML report are missing. Check console.txt and jmeter.log.\n' >&2
  exit 1
fi

printf '\nRun finished. Check sample errors in the summary and HTML report.\n'
printf 'Results: %s/results.jtl\n' "$run_dir"
printf 'HTML report: %s/html-report/index.html\n' "$run_dir"
