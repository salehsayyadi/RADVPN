#!/usr/bin/env bash
set -euo pipefail
source_sha="$(git rev-parse HEAD)"
dest="$RUNNER_TEMP/radvpn-android-evidence-$GITHUB_RUN_ID-$GITHUB_RUN_ATTEMPT"
git worktree add --detach "$dest" HEAD
cd "$dest"
git switch --orphan "$EVIDENCE_BRANCH"
git rm -rf --ignore-unmatch . >/dev/null
mkdir -p emulator
src="$GITHUB_WORKSPACE/dist/android"
[ ! -d "$src/emulator" ] || cp -R "$src/emulator/." emulator/
for file in "$src/build.txt" "$src/SHA256SUMS.txt"; do [ ! -f "$file" ] || cp "$file" .; done
printf 'Source: %s\nRun: https://github.com/%s/actions/runs/%s\nScope: actual Android emulator captures; physical-device VPN leak tests NOT RUN.\n' "$source_sha" "$GITHUB_REPOSITORY" "$GITHUB_RUN_ID" > README.txt
git add .
git -c user.name='RADVPN CI' -c user.email='radvpn-ci@users.noreply.github.com' commit -m 'Android native emulator and build evidence'
git push origin "HEAD:refs/heads/$EVIDENCE_BRANCH"
