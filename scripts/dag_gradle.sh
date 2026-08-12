#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repository_root=$(CDPATH= cd -- "$script_dir/.." && pwd)
dag_root="$repository_root/app-dag-browser"

cd "$dag_root"
exec ./gradlew "$@"
