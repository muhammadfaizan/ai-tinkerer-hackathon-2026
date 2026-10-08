# In-Tune — Development Log / Handoff

Read PROJECT_SPEC.md and DESIGN_SYSTEM.md first — they're the
authoritative architecture and design references and are kept current.
This file covers session history, decisions, and gotchas that aren't
duplicated there.

## Repo / infra
- github.com/muhammadfaizan/ai-tinkerer-hackathon-2026 (monorepo,
  npm workspaces: "apps/*")
- apps/server — Node/Express backend, deployed to Vercel at
  https://nudge-backend-olive.vercel.app. Env vars must be set
  separately in Vercel's dashboard (Settings -> Environment Variables)
  — local .env does NOT transfer on deploy. This caused a real bug
  earlier (silent OpenAI auth failure in prod) — check this first if
  a deployed endpoint ever returns error:true unexpectedly.
- apps/android-native — Kotlin + Jetpack Compose, package
  io.github.muhammadfaizan.intune.
- Built with Codex (OpenAI) as the coding agent for both sides.
  IMPORTANT: Codex's own sandbox has no JDK, so it can NEVER actually
  compile/run the Android app itself — it can only write code and
  claim correctness. Always verify yourself:
  `cd apps/android-native && ./gradlew :app:compileDebugKotlin`
  ...then run on-device. Don't trust "should work" from Codex on the
  Android side without this step.

## Current architecture (see PROJECT_SPEC.md for full detail)
- Backend pipeline: classify (stage 1) -> ground via Exa if ambiguous
  -> decide+write (stage 3). Originally OpenRouter+Exa+OpenAI; a prompt
  was just sent (not yet confirmed done) to move stage 1 to TypeSafe's
  Jev decision model (model id typesafe/jev-1.13, via OpenRouter's
  alpha Decisions API, NOT the normal chat endpoint) and move stage 3 +
  /parse-goals off direct OpenAI onto OpenRouter chat models, keeping
  a fallback to the old chat-based classifier if Jev fails.
- /nudge accepts either a single `activity` (Demo Mode) or a `session`
  (real multi-app tracking, cumulative 30-min window with a 30-min
  cooldown after a successful nudge — both were temporarily lowered
  for testing at various points, confirm they're back to production
  values: 30/30, look for named constants like TESTING_THRESHOLD_MIN).
- Real on-device tracking is CONFIRMED WORKING end-to-end: permission
  grant -> UsageStatsManager session detection (correctly skips self
  package AND the dynamically-resolved default launcher package,
  no hardcoded launcher list) -> WorkManager periodic check (15 min
  floor) -> real /nudge call -> notification with Accept/Dismiss/
  "Actually working" action buttons (direct notification actions via
  BroadcastReceiver, confirmed working) -> Room-backed history/points/
  streak, all local.
- Activity Recognition (walking/vehicle/still, no GPS) was scoped and
  a routineContext field wired into /nudge, feeding a "don't nudge
  during known commute/drop-off windows" rule — confirm current build
  status, this was mid-implementation.
- Gamification (Room: NudgeRecord, points, streak, level) is built and
  confirmed working via Demo Mode; Accept=+10pts, Dismiss/Already-
  aligned=0pts (never punitive), tied to Accept celebration animation.

## New spec section just added (not yet built) — "Activity, routines
## & daily plan"
Phone-only signals (step counter + Activity Recognition), explicitly
NO location of any kind in v1 (not even coarse), NO sleep/heart rate
(no wearable). Routines = patterns across days, labeled by the user in
a new Profile -> Routines tab. Daily plan = full-day timetable with
fixed context blocks (from labeled routines) + suggested goal blocks
in the gaps, delivered as an accept/edit card, accepted items become
inexact-scheduled reminders. New /plan endpoint planned, same
conventions as /nudge. Build order specified as 3 slices: activity log
-> routines -> daily plan. NOT STARTED YET.

## Known inaccurate copy — needs fixing
The in-app "nothing ever leaves your device" / "100% local" messaging
is FALSE and needs correcting before any public release: nudge/plan
requests send goals + app names/durations (+ soon routine labels) to
the backend and its AI providers. Room history/points/streak are
genuinely local. The spec now has corrected wording to use instead —
see "Honest privacy wording" in the new spec section. A Profile
"what we record" transparency section was already speced earlier and
should ship this corrected copy.

## Goals — mid-fix as of this log
Just sent an audit-first prompt to Codex: goals were behaving as fixed
after a redesign (Profile moved from a bottom tab to a top-right icon,
possibly dropping the edit entry point). Waiting on Codex's audit of
where goals are read/written and whether any add/edit UI path still
exists, before the actual fix (Profile Goals section: add/edit/remove,
DataStore-persisted, voice merge never replaces, MAX_GOALS constant
currently 3, don't retroactively change goalsSnapshot on old history).
**Check this first in the new chat** — see what Codex's audit found.

## Branding / naming — in progress, not fully verified
- App is "In-Tune" (hyphen), NOT "nudge" — a prompt was sent to remove
  user-facing "nudge" wording (notification title, "Today's nudge"
  labels) while explicitly NOT renaming internal identifiers
  (NudgeApi/NudgeWorker/NudgeRecord/the /nudge endpoint/the Vercel
  URL — renaming those would break the installed app). Confirm this
  landed.
- Package rename completed for pre-release distribution; the new
  application ID intentionally starts with fresh local app data.

## Design direction — settled after 3 iterations, currently building
## toward this
1st attempt: calm sage/coral — never fully tested before pivoting.
2nd: vibrant purple-orange gradient — user disliked, felt "too much."
3rd: muted earth-tone (sage/terracotta/cream) — user disliked, felt
"olive"/flat.
**CONFIRMED final direction**: "soft modern wellness" — extracted from
an actual gpt-image-2 generated reference image the user liked
(reference-b.png, Headspace/Calm-inspired). Real extracted values are
in DESIGN_SYSTEM.md: cream bg w/ subtle lavender-pink vignette, deep
indigo-purple #453F63 primary, lavender #E3DCF0 / peach #F7DCC4 chip
tints with circular icon badges, Nunito+Quicksand fonts, 28dp card
radius, single-strong-CTA nudge card pattern (not 3 equal buttons).
Nav restructured to 3 bottom tabs (Home/Logs/Progress) + top-right
Profile icon (matches reference, not a 4th bottom tab).
Latest screenshot review found: goal chips overflowing off-screen
(real bug, needs LazyRow or FlowRow), missing logo mark, wrong icon on
a goal chip (star instead of food-related), nudge card missing
headline/body size contrast — a fix prompt was sent, NOT YET CONFIRMED
in a follow-up screenshot.
Mascot images and app icon were generated once already at the OLD
purple-orange palette — these need regenerating at the new lavender/
peach/indigo palette (prompt was given, confirm if run).

## Persistent workflow notes (kept working well all session)
- Give Codex exact Logcat output / curl responses, not "it doesn't
  work" — this repeatedly found root causes fast (self-foreground-
  event bug, launcher-as-app bug, immutable notification channel with
  no sound, cooldown blocking test triggers, missing INTERNET
  permission, missing env vars on Vercel).
- Ask Codex to explicitly report root causes and self-audit against
  named anti-patterns — vague "done" reports have repeatedly hidden
  incomplete fixes (e.g. the red/green stray-color bug recurred twice
  across two different palette passes before being caught properly).
- Debug tools (Trigger check now / Reset cooldown / Demo Mode) exist
  and work — being moved behind a hidden tap-7-times-on-version-number
  pattern (like Android's own Developer Options) rather than visible
  on Home; confirm this landed.
- ESLint/Prettier set up for apps/server; ktlint set up for
  apps/android-native. Kotlin has no separate typecheck step —
  compileDebugKotlin IS the typecheck.

## Immediate next steps, in likely priority order
1. Check Codex's goals audit response, then apply the actual fix.
2. Confirm the Jev/OpenRouter backend model swap landed and re-verify
   all curl scenarios still behave correctly.
3. Confirm the latest chip-overflow/logo-mark/icon-mismatch UI fixes
   with a fresh screenshot comparison.
4. Regenerate mascot + app icon assets at the new confirmed palette.
5. Confirm the "nudge"->"In-Tune" branding text pass landed.
6. Fix the inaccurate "100% local" privacy copy before any public
   release.
7. When ready: build the activity/routines/daily-plan feature per the
   new spec section, in its specified 3 slices.
8. Later still: on-device LiteRT-LM model for stage 1 (spec'd, not
   started) — OnePlus 12 (Snapdragon 8 Gen 3, 12GB RAM) confirmed
   capable hardware; MediaPipe LLM Inference API is deprecated, use
   LiteRT-LM.
9. Before Play Store: write the actual privacy policy, complete Play's Data Safety form,
   confirm no background-location permission is used (v1 has none).

## 2026-10-07 — Backend refactor and security hardening
- `apps/server` now runs from TypeScript source: `src/app.ts`,
  `src/routes/nudge.ts`, and provider services in
  `src/services/openrouter.ts` and `src/services/exa.ts`. `server.js`
  is only the compiled Vercel export bridge. Route and service types
  are kept in their respective folders.
- Commands verified locally: `npm run format`, `npm run lint`,
  `npm run typecheck`, `npm run build`, and `npm test`. The final
  security/API suite had 13 passing tests plus routine-context tests.
- Server security now includes: 20kb JSON body cap; Zod validation;
  Helmet; generic errors; prompt/data delimiters; strict model-output
  validation; a `SERVICE_DISABLED=true` kill switch; constant-time
  `X-App-Key` middleware; and Upstash Redis-backed IP/install rate
  limiting. Provider errors and user data are no longer logged.
- Before deploying these server changes, set Vercel Production env
  vars: `APP_API_KEY`, `UPSTASH_REDIS_REST_URL`,
  `UPSTASH_REDIS_REST_TOKEN`, and `SERVICE_DISABLED=false`, in
  addition to the existing OpenRouter/Exa credentials. Production
  intentionally returns 503 if Upstash credentials are absent.
- Android currently does not send the new server headers. Before this
  server commit is deployed, add `X-App-Key: <APP_API_KEY>` to Android
  requests. Also add a stable non-secret `X-Install-Id` UUID to enable
  the second rate-limit key. Until then, `/nudge` and `/parse-goals`
  will return 401 after deployment.
- A tracked-files and git-history scan found no committed provider-key
  values; only `.env.example` placeholders were present. The scan was
  pattern-based, so it is not a substitute for rotating a credential
  that is suspected to have leaked elsewhere.

## 2026-10-07 — Server entitlements
- Added Redis-backed per-install entitlements in `apps/server`: missing
  or expired records resolve to the free tier (3 goals); pro permits 10.
  Records are stored as `entitlement:{installId}` and admin grants use
  source `admin`.
- Added `GET /entitlement` (app key + UUID `X-Install-Id`) and
  `POST /admin/entitlement` (separate constant-time Bearer
  `ADMIN_API_KEY`, 10/min IP limit, hidden with 404 when unconfigured).
  The service kill switch covers both routes.
- `/nudge` and `/parse-goals` now reject goal lists over the caller's
  effective tier with `403 {"error":"goal_limit"}`. Android was not
  changed: it must send a stable UUID `X-Install-Id` before a Pro grant
  can be resolved for that installation.

## 2026-10-07 — External-distribution update check
- Added authenticated `GET /app-version`. It is deliberately hidden with
  404 until all `APP_*` release metadata variables are valid; the global
  kill switch still runs first.
- Android checks at startup after a successful app render, at most once per
  12 hours. It only opens an HTTPS download URL through the system browser;
  it does not request package-install permission or install APKs itself.
- Release signing now reads only ignored `local.properties` keys:
  `storeFile`, `storePassword`, `keyAlias`, and `keyPassword`. Missing
  values intentionally produce an unsigned APK with a Gradle warning.

## 2026-10-08 — Android pre-release identity and release hardening
- Renamed the Android namespace and application ID to
  `io.github.muhammadfaizan.intune`. This is intentionally a new app:
  old installs cannot update into it, and Room/DataStore begin empty.
- Release now uses SDK 36, version `0.1.0` / code `1`, R8 and resource
  shrinking, disabled backup, and no Retrofit logging. A configured
  `local.properties` signs the APK; absent signing values yield an
  unsigned APK with a Gradle warning for local pre-release testing.
