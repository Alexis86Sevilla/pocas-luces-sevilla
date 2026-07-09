# Tasks: init-outage-platform (synced with implementation)

## Status: 13/13 resolved (12 completed, 1 removed by user decision)

## Phase 1: Foundation ✅
- [x] 1.1 Create domain models
- [x] 1.2 Create service interface + InjectionToken
- [x] 1.3 Add mock data arrays
- [x] 1.4 Implement `MockOutageService`

## Phase 2: Splash Gate ✅
- [~] 2.1 SplashGuard — REMOVED (user chose embedded element over route guard)
- [x] 2.2 SplashComponent — refactored to `output()` pattern, no Router
- [x] 2.3 Splash template — no skip button per user feedback

## Phase 3: Home + Cards ✅
- [x] 3.1 OutageCardComponent
- [x] 3.2 TestimonialCardComponent
- [x] 3.3 HomeComponent (includes splash embedding)

## Phase 4: Wiring + Cleanup ✅
- [x] 4.1 Routes: `/` → HomeComponent, `**` → `/`
- [x] 4.2 Root template cleanup
- [x] 4.3 Spanish metadata in index.html

## Fixes
- fix(service): remove duplicate OUTAGE_SERVICE token
- refactor(splash): remove skip button
- refactor(splash): embed splash as home element instead of separate route
