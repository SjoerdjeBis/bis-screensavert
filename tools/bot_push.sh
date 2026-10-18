#!/usr/bin/env bash
# Gebruik: tools/bot_push.sh "<commitbericht>" <paden...>
# Legt de wijzigingen in de paden vast en pusht ze naar de branch van tools/bot_branch.sh.
set -euo pipefail
message="$1"
shift
git config user.name "github-actions[bot]"
git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
git add "$@"
if git diff --cached --quiet; then
  echo "Niets nieuws om vast te leggen."
  exit 0
fi
git commit -m "$message"
target="$(tools/bot_branch.sh)"
# Meerdere jobs van dezelfde run kunnen tegelijk pushen; dan eerst de nieuwste versie ophalen.
for attempt in 1 2 3 4; do
  if git ls-remote --exit-code --heads origin "$target" >/dev/null; then
    git pull --rebase origin "$target"
  fi
  git push origin "HEAD:refs/heads/$target" && exit 0
  sleep 5
done
echo "Pushen naar $target lukte niet." >&2
exit 1
