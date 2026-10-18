#!/usr/bin/env bash
# Gebruik: tools/bot_pr.sh "<titel>"
# Maakt van de botbranch een pull request naar de hoofdbranch en voegt die samen zodra de build
# groen is. Doet niets als de bot niet op de hoofdbranch draaide of niets veranderde.
set -euo pipefail
title="$1"
target="$(tools/bot_branch.sh)"
if [ "$target" = "${GITHUB_REF_NAME}" ]; then
  exit 0
fi
if ! git ls-remote --exit-code --heads origin "$target" >/dev/null; then
  echo "Niets veranderd; geen pull request nodig."
  exit 0
fi
gh pr create --base "${DEFAULT_BRANCH}" --head "$target" --title "$title" \
  --body "Automatisch door de workflow *${GITHUB_WORKFLOW}* (run ${GITHUB_RUN_ID}). Wordt samengevoegd zodra de build groen is."
gh pr merge "$target" --auto --merge
# Pushes van deze bot starten zelf geen build; met de hand starten mag wel.
gh workflow run build.yml --ref "$target"
