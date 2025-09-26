#!/usr/bin/env bash

set -e

FALLBACK_VERSION=${FALLBACK_VERSION:-"0.0.0-SNAPSHOT"}
TRACK_DIRTY=${TRACK_DIRTY:-1}
DIRTY_HASH=${DIRTY_HASH:-1}
TRACK_COMMITS=${TRACK_COMMITS:-1}
TRACK_CI_RUN=${TRACK_CI_RUN:-0}
SNAPSHOT=${SNAPSHOT:-0}

function print_help_and_exit() {
  if [[ -n "$1" ]]; then
    echo "$1" >> /dev/stderr
    echo >> /dev/stderr
  fi

  echo "Usage: $(basename "$0") [options]"
  echo ""
  echo "Options:"
  echo "  -h, --help                              Show this help"
  echo "  -f, --fallback-version                  Set fallback version, default: 0.0.0-SNAPSHOT"
  echo "  --track-commits                         Include count of commits after version tag"
  echo "  -d, --track-dirty {1|0|t|f|true|false}  Track dirty status, default: 1"
  echo "  -d-                                     Don't track dirty status (equals to --track-dirty 0)"
  echo "  --dirty-hash                            Calculate hash of untracked files"
  echo "  --track-ci-run                          Include CI run ID if presented"

  if [[ -n "$1" ]]; then
    exit 1
  fi

  exit 0
}

function set_boolean_arg() {
  shopt -s extglob
  vname="${1##+(-)}"   # strip leading dashes
  vname="${vname//-/_}" # replace dashes with underscores
  vname="${vname^^}"    # uppercase

  shopt -s nocasematch
  if [[ "$2" == "1" || "$2" == "true" || "$2" == "t" ]]; then
    printf -v "$vname" "1"
  elif [[ "$2" == "0" || "$2" == "false" || "$2" == "f" ]]; then
    printf -v "$vname" "0"
  else
    shopt -u nocasematch
    print_help_and_exit "Incorrect/missing value for $1 - expected {boolean} but got \"$2\""
  fi
  shopt -u nocasematch
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help)
      print_help_and_exit
      ;;
    -f|--fallback-version)
      shift
      if [[ -z "$1" ]]; then
        print_help_and_exit "Missing fallback version value"
      fi
      FALLBACK_VERSION="$1"
      ;;
    -d)
      TRACK_DIRTY=1
      ;;
    -d-)
      TRACK_DIRTY=0
      ;;
    --track-dirty)
      shift
      set_boolean_arg "--track-dirty" "$1"
      ;;
    --dirty-hash)
      shift
      set_boolean_arg "--dirty-hash" "$1"
      ;;
    --track-commits)
      shift
      set_boolean_arg "--track-commits" "$1"
      ;;
    --track-ci-run)
      shift
      set_boolean_arg "--track-ci-run" "$1"
      ;;
    -s|--snapshot)
      SNAPSHOT=1
      TRACK_DIRTY=1
      DIRTY_HASH=0
      TRACK_CI_RUN=0
      break
      ;;
    *)
      print_help_and_exit
  esac
  shift
done

if [[ -z "$VERSION" && -f ".version" ]]; then
  VERSION=$(grep -v "^\\s*#" ".version" | head -n 1 | xargs)
fi

if [[ -n "$VERSION" ]]; then
  echo "$VERSION"
  return 0
fi

# set the version based on the git description of current commit
VERSION="$(git describe --tags --abbrev=0 --first-parent || true)"
if [[ -n "$VERSION" && "$TRACK_COMMITS" == "1" ]]; then
  N_COMMITS="$(git rev-list "$VERSION"..HEAD --count --first-parent || true)"
  if [[ -n "$N_COMMITS" && "$N_COMMITS" != "0" ]]; then
    N_COMMITS="-$N_COMMITS"
  else
    N_COMMITS=""
  fi
fi

if [[ -z "$VERSION" ]]; then
  VERSION="$FALLBACK_VERSION"
fi

# If run inside a GitHub Actions pipeline, append the GitHub run ID to the version string
if [[ "$TRACK_CI_RUN" == "1" && -n "$GITHUB_RUN_ID" ]]; then
    CI_RUN_ID="-#${GITHUB_RUN_ID}"
fi

if [[ "$TRACK_DIRTY" == "1" ]]; then
  # If this is non-empty, the working directory has changes or untracked files
  DIRTY="$(git status -s || true)"
  # If there are changes in the repo
  if [[ -n "$DIRTY" ]]; then
    if [[ "$TRACK_UNTRACKED" == "1" ]]; then
      # Loops over each untracked file and calculates a SHA256 hash of its contents
      UNTRACKED_FILES="$(git ls-files --others --exclude-standard)"
      UNTRACKED_FILES_HASH=""
      if [[ -n "$UNTRACKED_FILES" ]]; then
          while IFS= read -r UNTRACKED_FILE; do
            UNTRACKED_FILES_HASH="${UNTRACKED_FILES_HASH}$(md5sum "$UNTRACKED_FILE" | cut -f1 -d' '); "
          done <<<"$UNTRACKED_FILES"
      fi
    fi
    if [[ "$DIRTY_HASH" == "1" ]]; then
      # Gets verbose Git status output, including differences and upstream tracking info
      VERBOSE_DIFF="$(git status -vv || true)"
    fi
    DIRTY="-DIRTY-$(echo "${UNTRACKED_FILES_HASH}${VERBOSE_DIFF}" | md5sum | cut -f1 -d' ' | head -c 8)"
  fi
fi

# print the final version string
if [[ "$SNAPSHOT" == "1" ]]; then
  if [[ -n "$DIRTY" || -n "$N_COMMITS" ]]; then
    echo "$VERSION-SNAPSHOT"
  else
    echo "$VERSION"
  fi
else
  echo "$VERSION$N_COMMITS$DIRTY$CI_RUN_ID"
fi
