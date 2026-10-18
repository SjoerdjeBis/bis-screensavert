#!/usr/bin/env bash
# Waar de databot zijn wijzigingen neerzet. Op de hoofdbranch (beschermd: alleen met een groene
# build) niet direct, maar op een eigen branch per run; tools/bot_pr.sh maakt daar een pull request van.
set -euo pipefail
if [ "${GITHUB_REF_NAME}" = "${DEFAULT_BRANCH}" ]; then
  echo "bot/${BOT_NAME}-${GITHUB_RUN_ID}"
else
  echo "${GITHUB_REF_NAME}"
fi
