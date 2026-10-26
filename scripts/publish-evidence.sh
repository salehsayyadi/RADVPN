#!/usr/bin/env bash
set -euo pipefail
# Only synthetic-free empty-state screenshots and build/test diagnostics; never vaults or configs.
source_sha="$(git rev-parse HEAD)"
dest="$RUNNER_TEMP/radvpn-evidence-$GITHUB_RUN_ID-$GITHUB_RUN_ATTEMPT"
git worktree add --detach "$dest" HEAD
cd "$dest"
git switch --orphan "$EVIDENCE_BRANCH"
git rm -rf --ignore-unmatch . >/dev/null
mkdir -p native-ui
src="$GITHUB_WORKSPACE/dist"
if [ -d "$src/ui-evidence" ]; then cp -R "$src/ui-evidence/." native-ui/; fi
for f in "$src"/windows-*.txt "$src"/*tests*.json "$src"/*report*.txt "$src"/format-check.txt "$src"/SHA256SUMS.txt; do
  [ ! -f "$f" ] || cp "$f" .
done
printf 'Source commit: %s\nActions run: https://github.com/%s/actions/runs/%s\nScope: actual compiled WPF client captures, not a remote VPN or leak test.\n' "$source_sha" "$GITHUB_REPOSITORY" "$GITHUB_RUN_ID" > README.txt
git add .
git -c user.name='RADVPN CI' -c user.email='radvpn-ci@users.noreply.github.com' commit -m 'Native build and empty-state UI evidence'
git push origin "HEAD:refs/heads/$EVIDENCE_BRANCH"
