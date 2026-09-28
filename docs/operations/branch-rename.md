# Default branch rename: `master` → `main`

Code changes already made in this branch (`chore/infra-hardening`):

- `.github/workflows/deploy.yml`: trigger changed from `branches: [master]` to `branches: [main]`.
- `README.md`: deployment section now says "Pushes to `main`".
- `.github/dependabot.yml`: `target-branch: "main"` for every ecosystem.

**These only take effect once the branch on GitHub is actually renamed to
`main`.** Until then, this branch's `deploy.yml` (targeting `main`) is not the
one GitHub runs on pushes to `master` — the current `master` branch's
workflow file still targets `master`. Follow the exact order below.

## Order of operations

### 1. Merge feature branches into `master` locally

```bash
git checkout master
git pull origin master
git merge --no-ff fix/security-hardening
git merge --no-ff chore/infra-hardening
git push origin master
```

Resolve any conflicts before proceeding. Do not rename the branch until this
push succeeds and CI on `master` is green.

### 2. Rename the branch on GitHub

Either via the UI: **Settings → Branches → Default branch → rename `master` to `main`**,
or via the API:

```bash
gh api -X POST repos/Alexis86Sevilla/pocas-luces-sevilla/branches/master/rename -f new_name=main
```

GitHub automatically redirects the old branch name, updates open PRs, and
moves branch protection rules to `main`.

### 3. Update your local clone

```bash
git branch -m master main
git fetch origin
git branch -u origin/main main
git remote set-head origin -a
```

**Verify:**

```bash
git status                       # should say "On branch main, up to date with origin/main"
git remote show origin | grep "HEAD branch"   # should print "HEAD branch: main"
```

### 4. Push and confirm deploy fires correctly

The merge in step 1 already pushed `deploy.yml` with `branches: [main]` to
`master` (which is about to become `main`). Once the rename in step 2
completes, that same commit becomes the tip of `main`, so:

- The rename itself does **not** trigger a deploy (GitHub's rename is not a `push` event).
- The **next push to `main`** (e.g. a follow-up merge, or re-pushing the same
  tip with `git push origin main` if needed) evaluates `deploy.yml` as it
  exists on `main` at that commit — which already says `branches: [main]` —
  so it fires normally.
- If you need to confirm deploy works without waiting for the next real
  change, push an empty commit: `git commit --allow-empty -m "chore: trigger deploy on main" && git push origin main`.

**Verify:** GitHub Actions tab shows the "Deploy to VPS" workflow running
against `main`, and both `backend` and `frontend` jobs succeed.

### Rollback

If something goes wrong before other people have re-pointed their clones:

```bash
gh api -X POST repos/Alexis86Sevilla/pocas-luces-sevilla/branches/main/rename -f new_name=master
```

then reverse step 3 locally (`git branch -m main master`, `git branch -u origin/master master`).
GitHub's redirect (old name → new name) is one-directional per rename, so a
second rename is needed to go back — there is no automatic "undo".

## Note: stale remote branch

`origin/feature/district-grouping` exists on the remote and is unrelated to
this change. Left untouched — decide separately whether to merge or delete it.
