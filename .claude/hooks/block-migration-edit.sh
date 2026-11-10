#!/usr/bin/env bash
# Blocks edits to Flyway migrations that already exist. Applied migrations are
# checksummed by Flyway: changing one breaks production startup, and a deploy
# rollback only restores the JAR, never the database.
file_path=$(jq -r '.tool_input.file_path // empty')

case "$file_path" in
  */db/migration/V*.sql)
    if [ -e "$file_path" ]; then
      echo "Blocked: $file_path is an existing Flyway migration. Create a new V<n>__*.sql instead." >&2
      exit 2
    fi
    ;;
esac
exit 0
