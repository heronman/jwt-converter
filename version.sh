#!/usr/bin/env bash

set -e

CONFIG="$(realpath $(dirname "$0")/.version)"

if [ -f "$CONFIG" ]; then
  VERSION=$(grep -v "^\\s*#" "$CONFIG" | head -n 1)
  if [ -n "$VERSION" ]; then
    echo "$VERSION"
    exit
  fi
fi

# set the version based on the git description of current commit
# using "|| true" to override the "-e" option
last_tag="$(git describe --tags --abbrev=0 --first-parent || true)"
if [ "$last_tag" == "" ]; then
  # fallback to the default version on any git error
  echo "0.0.0-SNAPSHOT"
  exit
fi
commits_after_tag=$(git rev-list "$last_tag"..HEAD --count --first-parent)
if [ "$commits_after_tag" -gt 0 ]; then
  VERSION="${last_tag}-${commits_after_tag}"
else
  VERSION="${last_tag}"
fi

# If this is non-empty, the working directory has changes or untracked files
DIRTY="$(git status -s)"
# If run inside a GitHub Actions pipeline, append the GitHub run ID to the version string
if [[ -n "$GITHUB_RUN_ID" ]]; then
    VERSION="${VERSION}-${GITHUB_RUN_ID}"
fi

# If there are changes in the repo
if [[ "$DIRTY" != "" ]]; then
    # Loops over each untracked file and calculates a SHA256 hash of its contents
    UNTRACKED_FILES="$(git ls-files --others --exclude-standard)"
    UNTRACKED_FILES_HASH=""
    if [ -n "$UNTRACKED_FILES" ]; then
        while IFS= read -r UNTRACKED_FILE; do
            UNTRACKED_FILES_HASH="${UNTRACKED_FILES_HASH} $(sha256sum "$UNTRACKED_FILE"); "
        done <<<"$UNTRACKED_FILES"
    fi
    # Gets verbose Git status output, including differences and upstream tracking info
    VERBOSE_DIFF="$(git status -vv)"
    # If there are untracked files, append their hash to the version string
    if [[ "$UNTRACKED_FILES" != "" ]]; then
        VERSION="$VERSION-DIRTY-$(echo "$UNTRACKED_FILES_HASH" "$VERBOSE_DIFF" | sha256sum | cut -f1 -d' ' | head -c 8)"
    else
        VERSION="$VERSION-DIRTY-$(echo "$VERBOSE_DIFF" | sha256sum | cut -f1 -d' ' | head -c 8)"
    fi
fi
# print the final version string
echo "$VERSION"
