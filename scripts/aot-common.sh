#!/usr/bin/env sh

aot_hash_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    aot_hash_output=$(sha256sum "$1") || return 1
    printf '%s\n' "$aot_hash_output" | awk '{ print tolower($1) }'
  elif command -v shasum >/dev/null 2>&1; then
    aot_hash_output=$(shasum -a 256 "$1") || return 1
    printf '%s\n' "$aot_hash_output" | awk '{ print tolower($1) }'
  elif command -v certutil >/dev/null 2>&1; then
    aot_hash_output=$(certutil -hashfile "$1" SHA256) || return 1
    printf '%s\n' "$aot_hash_output" \
      | awk 'NR == 2 { gsub(/[[:space:]]/, ""); print tolower($0) }'
  else
    echo "No SHA-256 utility found (need sha256sum, shasum, or certutil)" >&2
    return 1
  fi
}

aot_runtime_marker() {
  aot_java_command=$(command -v java) || return 1
  aot_java_executable=$aot_java_command
  if command -v realpath >/dev/null 2>&1; then
    aot_resolved_java=$(realpath "$aot_java_command" 2>/dev/null || true)
    if [ -n "$aot_resolved_java" ]; then
      aot_java_executable=$aot_resolved_java
    fi
  elif readlink -f "$aot_java_command" >/dev/null 2>&1; then
    aot_java_executable=$(readlink -f "$aot_java_command")
  fi

  aot_java_home=$(dirname "$(dirname "$aot_java_executable")")
  if [ -f "$aot_java_home/release" ]; then
    printf '%s\n' "$aot_java_home/release"
  else
    printf '%s\n' "$aot_java_executable"
  fi
}

aot_current_metadata() {
  aot_metadata_jar_hash=$(aot_hash_file "$1") || return 1
  aot_metadata_runtime_marker=$(aot_runtime_marker) || return 1
  aot_metadata_runtime_hash=$(aot_hash_file "$aot_metadata_runtime_marker") || return 1
  printf 'format=1\njar.sha256=%s\nruntime.sha256=%s\n' \
    "$aot_metadata_jar_hash" "$aot_metadata_runtime_hash"
}

aot_write_metadata() {
  aot_metadata_temp=$(mktemp "${2}.XXXXXX") || return 1
  if aot_current_metadata "$1" > "$aot_metadata_temp"; then
    mv -f "$aot_metadata_temp" "$2"
  else
    rm -f "$aot_metadata_temp"
    return 1
  fi
}

aot_metadata_is_current() {
  [ -f "$2" ] || return 1
  aot_expected_metadata=$(aot_current_metadata "$1") || return 1
  aot_stored_metadata=$(tr -d '\r' < "$2") || return 1
  [ "$aot_stored_metadata" = "$aot_expected_metadata" ]
}
