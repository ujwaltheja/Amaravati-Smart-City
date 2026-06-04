# Amaravati Smart City — Priority Audit & Roadmap

**Status**: Prototype → Production (Target: Google Play launch)  
**Date**: June 4, 2026  
**Scorecard**: Production Readiness 55/100 | Performance 70/100 | Visual Quality 65/100 | Monetization 20/100 | UX 60/100 | Code Quality 75/100

---

## Executive Summary

The Amaravati Smart City project has a **strong simulation foundation** with clean Kotlin code, modern Android stack (Compose + Room + SceneView), and rich game systems (economy, emergencies, road graphs, service coverage). However, it is **not production-ready** and requires substantial work across three areas:

1. **Critical business problems**: No monetization, missing first-session onboarding, unclear differentiation from competitors.
2. **Critical technical debt**: Tightly coupled ViewModel/UI, lack of clean architecture, potential performance cliffs at 10k+ objects.
3. **Content & localization**: Generic assets and English-only; missing Amaravati-specific landmarks and Telugu culture.

Expected effort to production readiness: **12–16 weeks**. Below is prioritized track list.

---

## Critical Issues (Must fix before soft launch)

### 1. No Monetization Implementation

| Aspect | Detail |
|--------|--------|
| **Problem** | Zero revenue streams: no ads, IAP, or subscriptions despite monetizable deep systems. LTV ≈ 0. |
| **Impact** | Cannot fund UA; business model non-existent. |
| **Root Cause** | Monetization layer not started; dependencies (Google Play Billing, ad SDK) missing. |
| **Recommended Fix** | Decide on model (soft + premium currency, cosmetic packs, ads); integrate Google Play Billing and rewarded ad SDK; design balanced economy with non-pay-to-win progression. |
| **Effort** | 2–4 weeks initial + ongoing balance. |
| **Priority** | **CRITICAL** |
| **Ownership** | Product + Backend |
| **Acceptance Criteria** | - Google Play Billing integrated and tested. - At least 2 IAP SKUs (cosmetic + utility) priced and configured. - Rewarded ad provider SDK integrated. - Economy rebalanced if needed. |

**Next Steps** 
- [ ] Finalize monetization model (recommend: soft currency earned in-game, premium currency for cosmetics + battle pass + optional 2x income boost)
- [ ] Add `PlayBilling` and `com.google.android.gms:play-services-ads` or `com.mopub:mopub-sdk` to `libs.versions.toml`
- [ ] Design premium currency sinks and cosmetic packs (themed building skins, landmark bundles, city themes)
- [ ] Implement soft/premium currency UI in main HUD and shop overlay

---

### 2. Release Build Not Google Play Ready

| Aspect | Detail |
|--------|--------|
| **Problem** | `release` build uses debug signing config and has `minifyEnabled = false`; code is not obfuscated. |
| **Impact** | Cannot ship to Play Store; code easily reverse-engineered; no protection against APK repacking. |
| **Root Cause** | Prototype build.gradle setup; no release signingConfig defined. |
| **Recommended Fix** | Configure proper release signingConfig (keystore or Play App Signing), enable minify, tune Proguard rules for Kotlin + SceneView + Room. |
| **Effort** | 1–2 days. |
| **Priority** | **CRITICAL** |
| **Ownership** | DevOps / Build Engineer |
| **Acceptance Criteria** | - Release build signed with proper keystore (stored securely, not in repo). - `isMinifyEnabled = true` in release. - Proguard rules cover Kotlin, Filament, Room reflections. - Build passes Play Console pre-launch checks. |

**Next Steps**
- [ ] Create release keystore (keytool, check into secure storage)
- [ ] Configure `signingConfigs.release` with keystore path + credentials (use BuildConfig secrets or env vars)
- [ ] Enable minify and tune Proguard: `-keep public class com.uc.amaravatismartcity.game.**` and library rules
- [ ] Test release build on device; verify app runs and saves/loads work
- [ ] Run Play Console pre-launch checks and fix any warnings

---

### 3. Missing First-Session Tutorial & Onboarding

| Aspect | Detail |
|--------|--------|
| **Problem** | No guided tutorial; players must discover build, rotate, bulldoze, heatmap, and emergencies themselves. High early churn vs. SimCity BuildIt / TheoTown. |
| **Impact** | D1 retention likely <40%; abandonment in first 10 minutes among casual players. |
| **Root Cause** | FocusIon simulation and HUD; onboarding deferred. |
| **Recommended Fix** | Add scripted first-session missions with overlays: "Build two roads → Place a house → Respond to first event → Unlock heatmaps". Gate advanced controls until explained. Contextual hints on first access to each tool. |
| **Effort** | 2–3 weeks (design + UI overlays + state machine). |
| **Priority** | **CRITICAL** |
| **Ownership** | Game Design + UI |
| **Acceptance Criteria** | - Tutorial tracks first 5 missions as "scripted" with UI overlays and auto-highlighting tile placement. - Advanced HUD elements (photo mode, detailed heatmaps, policies) locked until rank 300+. - Tutorial can be skipped but replay option in settings. - Tutorial completion metric tracked in analytics. |

**Next Steps**
- [ ] Design tutorial flow: mission sequence, overlay visuals, when to unlock each tool
- [ ] Add `TutorialState` enum / tracker to `GameState` (e.g., `tutorialProgress: Int`, `tutorialSkipped: Boolean`)
- [ ] Create overlay composables for "place this building here" + "tap to build" + "well done!" with animations
- [ ] Update `checkGoals` to drive tutorial state and show contextual hints
- [ ] Implement feature-gating: `if (tutorialProgress < X) hideElement(...)` for advanced panels
- [ ] Test with 5 cold players; measure task completion times

---

### 4. Simulation & Game Logic Tightly Coupled to ViewModel & UI

| Aspect | Detail |
|--------|--------|
| **Problem** | `GameViewModel` (512 lines) mixes persistence (Room), business logic (advanceSimulation), and UI messages (news strings). `AmaravatiGameSurface` (~1,071 lines) mixes 3D setup, HUD, input logic, and validation. Results: hard to test, maintain, extend; high regression risk. |
| **Impact** | Every feature touches 2+ god classes; fixing bugs is risky; adding monetization or new systems is error-prone. |
| **Root Cause** | Organic growth; no domain / application / UI layer separation. |
| **Recommended Fix** | Extract clean architecture: ( 1) Pure `CitySimulation` engine with all drift/economy logic. (2) `SimulationRepository` interface + `RoomGameRepository` impl for persistence. (3) `GameInteractor` or use-cases for high-level actions (placeBuilding, triggerEvent, etc.). (4) Split AmaravatiGameSurface into world rendering, HUD composables, and input controller. |
| **Effort** | 2–3 weeks iterative refactor (can be done incrementally). |
| **Priority** | **CRITICAL** |
| **Ownership** | Architecture / Senior Engineer |
| **Acceptance Criteria** | - CitySimulation is a pure function `fun advance(state: GameState, dt: Float, items: List<PlacedItem>): GameState`. - GameViewModel only calls CitySimulation and updates StateFlow. - All persistence I/O goes through injected `GameRepository`. - AmaravatiGameSurface <= 500 lines; HUD in separate composables in `ui/hud/` folder. - Unit tests for CitySimulation.advance() with fixed seed pass. |

**Next Steps**
- [ ] Create `game/CitySimulation.kt`: extract advanceSimulation logic into pure, testable function
- [ ] Create `game/GameRepository.kt` interface and `db/RoomGameRepository.kt` impl; inject into ViewModel
- [ ] Create `game/GameInteractor.kt` for high-level use cases (placeBuilding, saveGame, etc.)
- [ ] Move HUD panels to `ui/hud/CityHUD.kt`, `ui/hud/BuildDock.kt`, `ui/hud/Minimap.kt`, etc.
- [ ] Move input logic to `game/InteractionController.kt`; simplify AmaravatiGameSurface
- [ ] Add unit tests: `CitySimulationTest.kt` tests advanceSimulation with fixed seed
- [ ] On each refactor step, run full test suite and validate gameplay on device

---

### 5. Traffic Simulation Not Based on Road Graph Pathfinding

| Aspect | Detail |
|--------|--------|
| **Problem** | Vehicles are **decorative**, not agent-based; they don't compute origin-destination paths on the road graph. Traffic metrics (averageCongestion, emergencyDelay) are global heuristics, not per-edge loads. Hardcore players expecting depth similar to Cities: Skylines will feel the mechanic is shallow. |
| **Impact** | Positioned as "smart city simulator" but traffic feels arcade-like; missed opportunity to tie gameplay depth. |
| **Root Cause** | Complexity of agent pathfinding; chosen simplified approach for MVP. |
| **Recommended Fix** | Design **per-road load simulation**: (1) Identify residential, commercial, industrial clusters. (2) Assign demand flows between zones (e.g., residential workers → commercial jobs). (3) Route flows across RoadGraph; accumulate per-edge usage. (4) Keep vehicle visuals simple but tie emergencyDelay and congestion to actual flows. Optional later: add commute time penalties if happiness/jobs mismatch. |
| **Effort** | 3–4 weeks initial (data model + pathfinding integration). |
| **Priority** | **CRITICAL** (for smart city positioning) |
| **Ownership** | Simulation / Systems |
| **Acceptance Criteria** | - Per-road edge usage tracked in RoadGraph (replace or extend usageByCell). - Residential-to-job flows calculated and routed via BFS each tick. - averageCongestion reflects actual per-edge loads, not global heuristic. - Vehicle positions tied to assigned routes (even if visuals simplified). - Test map with 50 residences + 20 jobs shows realistic non-uniform traffic. |

**Next Steps**
- [ ] Design demand/supply model: each residential outputs workers, each commercial inputs workers
- [ ] Extend `RoadGraph` to track per-edge flow or per-edge usage separately
- [ ] Implement flow routing: for each worker, find nearest job via road graph; mark edges in path
- [ ] Update `buildRoadGraph` to accumulate per-edge usage from all worker paths
- [ ] Recompute averageCongestion from per-edge loads
- [ ] Update vehicle spawning to assign routes based on flow demands (even if visual motion is simple)

---

## High Priority Issues (Do before open beta)

### 1. Lack of Deep Amaravati & Telugu Localization

| Aspect | Detail |
|--------|--------|
| **Problem** | Game feels generic "futuristic city", not uniquely Amaravati. Uses English-only strings, generic assets ("IT Skyscraper", "Apartment Block"), and no Telugu localization or Amaravati landmarks. Misses local emotional resonance and press differentiator. |
| **Impact** | Weak local market appeal; weak PR story for Andhra Pradesh / tech communities. |
| **Root Cause** | Using generic asset packs and English scaffold. |
| **Recommended Fix** | (1) Add Telugu localization to all strings (use Android Localization framework). (2) Add specific Amaravati landmarks: Secretariat, Assembly, High Court, Riverfront Park, AP Knowledge City, etc., as buildable/prestige buildings. (3) Extend eventPool with local events (Tirupati festival, Visakha port, Hydrabad tech summit). (4) City name + landmarks in Telugu/English toggle. |
| **Effort** | 2–3 weeks (content + art + localization). |
| **Priority** | HIGH |
| **Ownership** | Content / Localization |
| **Acceptance Criteria** | - All strings in `strings.xml` localized to Telugu (`strings-te.xml`). - 5+ Amaravati-specific landmark buildings added to BuildingCatalog with custom art or distinct models. - Event pool expanded with 10+ local cultural/political events. - City name and rank defaults to Telugu; player can toggle language in settings. |

**Next Steps**
- [ ] Create `strings-te/strings.xml` with Telugu translations
- [ ] Design Amaravati landmark buildings (Secretariat, Assembly, etc.) with unique descriptions and impacts
- [ ] Commission or source unique 3D models for landmarks (or adapt existing assets with unique scale/color)
- [ ] Add local event strings to eventPool: Tirupati Brahmotsavams, Visakha Port inauguration, Seed Capital milestone, etc.
- [ ] Add language toggle in MainMenuScreen and settings
- [ ] Test with Telugu speakers for cultural accuracy and flavor

---

### 2. No Daily/Weekly Missions, Live-Ops, or Meta-Progression

| Aspect | Detail |
|--------|--------|
| **Problem** | Missions are linear and static; no daily/weekly tasks, rotating events, or account-level progression (mayor level, unlocks, prestige). Poor D1/D7/D30 retention vs. competitors (SimCity BuildIt, TheoTown) with live-ops. |
| **Impact** | Retention metrics likely <30% D7; players lack habit-forming loops. |
| **Root Cause** | Early stage; live-ops not prioritized. |
| **Recommended Fix** | (1) Introduce daily/weekly missions (e.g., "Earn ₹10K this week", "Build 3 green spaces", "Achieve 85+ happiness"). (2) Add mayor-level progression separate from rank (gained by completing missions; unlocks cosmetics, policies). (3) Rotating monthly events (themed building packs, limited-time challenges). (4) Battle pass skeleton (free + premium track with cosmetics). |
| **Effort** | 3–4 weeks. |
| **Priority** | HIGH |
| **Ownership** | Game Design / Systems |
| **Acceptance Criteria** | - Daily/weekly mission data model in DB. - UI for mission list and progress tracking. - Mayor level increments on mission completion; reaches 100 by week 4 of play. - Rotating monthly event framework (events loaded from config or server). - Battle pass UI skeleton (premium/free tracks). |

**Next Steps**
- [ ] Design daily mission pool (20+ variants); weekly missions (15+ variants); track completion state in GameState
- [ ] Add `MayorLevel` entity and progression curve (XP earned per mission type)
- [ ] Implement UI for mission list, progress bars, and rewards (coins, cosmetics)
- [ ] Add rotating event system: events have start/end dates; pull from event catalog each day
- [ ] Create battle pass progression (50 tiers, free + premium cosmetics)
- [ ] Seed with ~2 weeks of events; plan out a monthly content calendar

---

### 3. Economy Tuning is Static & Handcrafted; No Content Pipeline

| Aspect | Detail |
|--------|--------|
| **Problem** | All building costs, impacts, and economy thresholds are hardcoded in `BuildingCatalog.kt`. No external tuning tool, no balancing curve, no scaling by era/rank. Progression pacing likely off; players may hit dead-ends (too expensive) or feel bored (too easy). |
| **Impact** | Either too punishing or too generous; unpredictable LTV. |
| **Root Cause** | All values hard-coded; no content tooling. |
| **Recommended Fix** | (1) Export building data to JSON/CSV (e.g., `config/buildings.json`); load at startup instead of hardcoded. (2) Create balancing spreadsheet with macro controls: inflation by rank, cost multiplier, income scales. (3) Add in-game economy tuning UI (admin mode) to tweak at runtime and A/B test. (4) Implement economy epochs: early (low costs, simple buildings), mid (progressive pricing, emergencies matter), late (high costs, prestige buildings). |
| **Effort** | 2–3 weeks. |
| **Priority** | HIGH |
| **Ownership** | Design / Systems |
| **Acceptance Criteria** | - Building data loaded from external JSON file. - Balancing spreadsheet documents income/cost/rank progression. - Admin panel allows runtime economy tuning. - Three economy epochs defined; costs scale cleanly across ranks. - Playtesting with external users shows no dead-end scenarios in first 30 min. |

**Next Steps**
- [ ] Create `assets/config/buildings.json` with all building data serialized
- [ ] Refactor `BuildingCatalog.defaultCatalog()` to deserialize JSON file
- [ ] Create balancing spreadsheet in Sheets: cost, income, jobs, impacts by building; test scaling curves
- [ ] Add admin UI in-game (toggle via long-press on title bar?) to adjust economy multipliers at runtime
- [ ] Playtest with 5 external players; collect feedback on pacing and economy balance

---

### 4. Potential Performance Issues at 10,000+ Objects

| Aspect | Detail |
|--------|--------|
| **Problem** | Each frame iterates O(N) over all `placedItems` to check distance/culling; no spatial partitioning (quadtree/grid). At 10k+ objects in late-game cities, this risks FPS drops and CPU spikes on mid-tier devices. |
| **Impact** | Late-game cities lag; players may quit before reaching end-game content. |
| **Root Cause** | Current culling is simplistic; no spatial structure. |
| **Recommended Fix** | Introduce chunked grid: divide world into fixed-size chunks (e.g., 50m × 50m); track items per chunk. Each frame: (1) compute player-visible chunk range, (2) update/cull only visible chunks, (3) render only visible items. Add LOD: detail level decreases with distance. Profile on mid-tier device (Snapdragon 680 or similar). |
| **Effort** | 2–4 weeks (implementation + tuning + testing). |
| **Priority** | HIGH |
| **Ownership** | Performance / Rendering |
| **Acceptance Criteria** | - Spatial grid implemented; items partitioned into chunks. - Only visible chunks rendered/simulated each frame. - LOD levels defined (full detail < 20m, mid 20–50m, simple > 50m). - 10,000 item test city runs at 30+ FPS on mid-tier device (Pixel 4a or equivalent). - CPU profiling shows simulation + culling < 16ms/frame. |

**Next Steps**
- [ ] Design chunk grid structure: chunk size, update frequency, visibility range
- [ ] Implement `SpatialGrid<PlacedItem>` class with chunk management
- [ ] Refactor rendering loop to query grid instead of iterating all items
- [ ] Add LOD: high-detail models < 20m, LOD1 20–50m, simple quads > 50m
- [ ] Create 10k-object test city and profile on Pixel 4a; measure FPS and CPU/GPU time
- [ ] Optimize until target (30 FPS, <16ms frame time) achieved

---

### 5. Cheating via Unprotected Local Database

| Aspect | Detail |
|--------|--------|
| **Problem** | Saves can be edited via local DB with root tools; no checksums or validation; if you add social/competitive features, they're easily abused. |
| **Impact** | Leaderboards/multiplayer meaningless without integrity checks. |
| **Root Cause** | Offline-only prototype; no server auth. |
| **Recommended Fix** | For single-player offline: (1) Obfuscate saves (encrypt critical fields). (2) Add checksums to `GameStateEntity`. (3) Detect tampering and flag suspect saves. (4) For competitive / social features: move critical progress to backend; use Play Games Services or custom auth. |
| **Effort** | 1–2 weeks for offline hardening; 3–4 weeks if backend is needed. |
| **Priority** | HIGH (if any multiplayer/social planned) |
| **Ownership** | Backend / Security |
| **Acceptance Criteria** | - Checksums added to GameStateEntity; verified on load. - Saves encrypted at rest (AES). - Tamper detection logs suspicious saves. - If multiplayer planned: backend auth + server-side leaderboards spec'd out. |

**Next Steps**
- [ ] Add checksum field to `GameStateEntity` and `PlacedItemEntity`
- [ ] Implement `encryptSave(state)` / `decryptAndVerify(encrypted)` using Android Keystore
- [ ] Update `saveGame()` and `loadGame()` to use encryption/checksums
- [ ] Log tamper events for debugging (don't expose to user)
- [ ] If multiplayer planned: design backend save sync (Firebase or custom service)

---

## Medium Priority Issues (Improve before launch)

### 1. Overloaded HUD & Lack of Progressive Feature Unlocks

**Problem**: Most controls available from start; HUD dense and overwhelming for new players.  
**Fix**: Gate heatmaps, photo mode, advanced road types, policies, and detailed system panels by rank/mission.  
**Effort**: 1–2 weeks. **Priority**: MEDIUM.

**Tasks**:
- [ ] Audit all HUD elements; decide unlock gate for each (e.g., heatmaps at rank 500, policies at rank 1200)
- [ ] Update visibility conditions in AmaravatiGameSurface: `if (state.population >= threshold) show(element)`
- [ ] Add first-unlock animations / "New feature available!" toasts
- [ ] Test that new player sees < 50% of controls in first session

---

### 2. No Explicit Land Value or District System

**Problem**: All tiles behave similarly; no per-tile variation beyond global metrics. No adjacency bonuses.  
**Fix**: Introduce per-tile/district "land value" influenced by adjacency (green space, low pollution, service coverage, high happiness). Buildings on high-value land generate more tax/happiness. Adds emergent district formation.  
**Effort**: 3–4 weeks. **Priority**: MEDIUM.

**Tasks**:
- [ ] Design land value model: base + adjacency bonuses + coverage influence
- [ ] Add `landValueMap: Map<GridCell, Float>` to simulation state
- [ ] Update `calculateBalances` to factor in land value for tax/housing capacity calculations
- [ ] Implement heatmap visualization for land value
- [ ] Tune formula to create organic district clustering

---

### 3. No Analytics or Telemetry

**Problem**: Cannot see where players churn or what they build; no data for decisions.  
**Fix**: Integrate Firebase Analytics; track tutorial completion, session length, main funnels, building preferences, economy state at key milestones.  
**Effort**: 1 week. **Priority**: MEDIUM.

**Tasks**:
- [ ] Add Firebase Analytics to app/build.gradle
- [ ] Track tutorial completion, first building placement, first event, rank milestones, session length
- [ ] Define custom events: "building_placed", "emergency_handled", "mission_completed"
- [ ] Set up Firebase Console dashboard to monitor retention, progression funnels, build preferences

---

### 4. SceneView / Filament Model Lifecycle Not Explicit

**Problem**: Model instances created in `remember` without explicit disposal; potential memory leak with many unique models.  
**Fix**: Introduce model cache, explicit release, and resource pooling; profile RAM usage.  
**Effort**: 1–2 weeks. **Priority**: MEDIUM.

**Tasks**:
- [ ] Create `ModelCache` class; pool and reuse model instances by asset
- [ ] Implement explicit `release()` when model is no longer visible or city is cleared
- [ ] Profile RAM usage on device with 1000+ built items; target <400MB resident set
- [ ] Add cache hit/miss metrics to debug telemetry

---

### 5. Insufficient Testing Coverage

**Problem**: No visible unit tests for simulation logic, placement, road graph, economy.  
**Fix**: Add tests for `CitySimulation.advance()`, `buildRoadGraph()`, `calculateBalances()`, `canPlaceOnGrid()`, and edge cases (negative money, max population, dense roads).  
**Effort**: 1–2 weeks. **Priority**: MEDIUM.

**Tasks**:
- [ ] Create `CitySimulationTest.kt` with tests for advance() with fixed seed
- [ ] Create `RoadGraphTest.kt` for pathfinding and congestion logic
- [ ] Create `PlacementTest.kt` for canPlaceOnGrid edge cases
- [ ] Achieve >70% coverage for game logic (CitySystems.kt, GameState logic)
- [ ] Add instrumented tests for Room persistence

---

## Nice-to-Have Improvements (Post-launch or lower cadence)

- **Weather system tied to events**: Monsoon storms, fog, heatwaves linked to eventPool entries. (2–3 weeks)
- **Cinematic camera & photo sharing**: Enhance photo mode with filters, angles, share intents. (1–2 weeks)
- **Advanced policies & smart city dashboards**: Traffic policies, energy policies, district-level dashboards. (4+ weeks)
- **Rich Amaravati landmarks & cosmetic packs**: Themed building packs, unique landmark bundles as paid cosmetics. (ongoing art)
- **Multiplayer / shared maps**: Collaborative or competitive city building. (8+ weeks)
- **Mod support**: Steam Workshop-style building/event packs created by community. (6+ weeks)

---

## Roadmap: Phases to Production

### Phase 1: Critical Fixes & Architecture (Weeks 1–6)

**Goals**: Establish clean architecture, onboarding, and monetization foundation.

- [ ] **Weeks 1–2**: Release build setup (signing, minify, Proguard). Refactor architecture: extract CitySimulation, GameRepository.
- [ ] **Weeks 2–3**: Implement first-session tutorial with scripted missions and overlays; feature-gate advanced panels.
- [ ] **Weeks 3–4**: Integrate Google Play Billing and rewarded ad SDK; add soft/premium currency UI.
- [ ] **Weeks 4–6**: Rebalance economy; implement flow-based traffic simulation (per-edge loads).
- **Deliverable**: Soft-launch-ready build with clean code, onboarding, and monetization scaffolding.

### Phase 2: Performance & Optimization (Weeks 7–10)

**Goals**: Ensure scalability to 10k+ objects; smooth late-game experience.

- [ ] **Weeks 7–8**: Implement spatial grid and chunked culling; add LOD levels.
- [ ] **Weeks 8–9**: Profile and optimize simulation (batch updates, avoid recomputing RoadGraph multiple times).
- [ ] **Weeks 9–10**: Low-end device preset; dynamic FPS/quality scaling.
- **Deliverable**: 10k-object test city runs at 30+ FPS on mid-tier device.

### Phase 3: Content & Live-Ops (Weeks 11–14)

**Goals**: Expand content, retention, and Amaravati differentiation.

- [ ] **Weeks 11**: Telugu localization and Amaravati landmark buildings.
- [ ] **Weeks 12**: Daily/weekly missions, mayor-level progression, battle pass framework.
- [ ] **Weeks 13**: Rotating monthly events (themed packs, challenges); land value system.
- [ ] **Week 14**: Playtest with external users; collect feedback on economy, progression, retention.
- **Deliverable**: Open beta-ready build with live-ops systems and strong local flavor.

### Phase 4: Polish & Launch Prep (Weeks 15–16+)

**Goals**: Bug fixes, analytics integration, store listing.

- [ ] **Week 15**: Integrate Firebase Analytics; fix reported bugs; polish animations and UI transitions.
- [ ] **Week 16**: Finalize app store listing (screenshots, description, category); submit to Play Console.
- [ ] **Ongoing**: Soft-launch A/B testing; iterate on economy and retention based on telemetry.
- **Deliverable**: Google Play Store launch.

---

## Ownership & Team

| System | Owner | Notes |
|--------|-------|-------|
| Monetization & Play Integration | Product / Backend | Critical path; start early. |
| Build & Release | DevOps / Build Engineer | 1–2 days, but blocks launch. |
| Tutorial & Onboarding | Game Design / UI | Directly impacts D1 retention. |
| Architecture Refactor | Senior Engineer | Foundation for all future work. |
| Traffic Simulation | Systems / Simulation | Core to "smart city" positioning. |
| Performance & Optimization | Rendering / Graphics | Enables large cities; mid-tier focus. |
| Amaravati Localization | Content / Localization | Local appeal and press differentiator. |
| Live-Ops & Missions | Game Design / Backend | Not critical for soft-launch but essential for D7+ retention. |

---

## Success Metrics

| Metric | Target | Measurement |
|--------|--------|-------------|
| **Production Readiness** | 85+ / 100 | Audit score; feature completeness; crash rate < 0.1%. |
| **D1 Retention** | ≥50% | Cohort who complete tutorial. |
| **D7 Retention** | ≥25% | Cohort who return within 7 days. |
| **D30 Retention** | ≥8% | Cohort who return within 30 days. |
| **Performance (Late Game)** | 30+ FPS | With 10k objects on mid-tier device. |
| **Install Size** | <150 MB | APK size (compressed); assets optimized or streamlined. |
| **LTV (first 30 days)** | ≥$0.50 USD | Monetization hookup working; baseline conversion. |
| **Store Rating** | ≥4.0 stars | After first 500 reviews; indicates quality and onboarding success. |

---

## De-Risk Checklist

Before going to production (launch to Play Store):

- [ ] Release build successfully builds and is signed with release keystore.
- [ ] Minify enabled; code obfuscated; no crashes when running release APK.
- [ ] Tutorial tested with 5 cold users; 80% complete tutorial in first session.
- [ ] Monetization flow tested end-to-end: IAP purchase and ad reward granted correctly.
- [ ] 10k-object test city runs at 30+ FPS on Pixel 4a or equivalent.
- [ ] Saves are encrypted and checksummed; tampering detected.
- [ ] Telemetry logs key events (tutorial, first build, first rank-up, session start/end).
- [ ] Store listing (screenshots, description, privacy policy) is finalized and compliant.
- [ ] Play Console pre-launch checks pass (permissions, targeting, content rating).
- [ ] Soft-launch in Canada / Australia for 2 weeks; D1/D7 retention ≥40% / ≥15%.
- [ ] No P1/P2 crashes reported during soft-launch; economy tuning validated by players.

---

## Appendix: Detailed Issue Log

### Issue: Game never "ends"; no fail state

**Problem**: No explicit win or loss condition; game continues indefinitely unless player abandons.  
**Impact**: Some players feel aimless after early excitement fades.  
**Fix**: Add milestone goals (e.g., "500k sustainable city" for smart capital); offer prestige/reset mechanics or sandbox/"challenge" modes.  
**Effort**: 2+ weeks. **Priority**: Medium.

---

### Issue: No explicit per-building maintenance costs

**Problem**: Economy is "income only"; no ongoing per-building upkeep.  
**Impact**: Heavy cities don't feel more expensive to maintain; budget management shallow.  
**Fix**: Add maintenance cost to each building (per month, proportional to size/tier); update tax-income calculation.  
**Effort**: 1 week. **Priority**: Medium.

---

### Issue: Vehicles purely decorative; no pathfinding

**Covered above in Critical Issue #5.**

---

### Issue: No terrain or natural constraints

**Problem**: Every tile is buildable; no hills, rivers, or water features limit placement (except "Krishna riverfront" is flavor text).  
**Impact**: City layout lacks organic variation; every city looks similar.  
**Fix**: Add procedural terrain variation (heights, water bodies, forests); block placement on water, high slopes.  
**Effort**: 3–4 weeks. **Priority**: Low (nice-to-have).

---

### Issue: Citizen AI only aggregate; no individual agents

**Problem**: `population` is a single integer; no households, job matching, or emergent behaviors.  
**Impact**: Depth limited vs. Cities: Skylines; some hardcore players may feel it's not a "real" city sim.  
**Expectation**: This is acceptable for mobile MVP, but mention in positioning if going against desktop simulators.  
**Mitigation**: Introduce per-district or per-block micro-stats (% unemployed, % in transit, % happy) without full agent sim.  
**Effort**: 4+ weeks for meaningful depth. **Priority**: Low.

---

### Issue: No zoning system

**Problem**: All buildings are explicit placements; no "designate a zone and let it auto-develop" mechanic.  
**Impact**: Players must manually place each building; slower pace; less emergent city growth.  
**Fix**: Introduce zoning overlay; zone fills with appropriate buildings based on demand (residential, commercial, industrial).  
**Effort**: 4+ weeks with new UX and auto-placement logic. **Priority**: Low (post-launch feature).

---

### Issue: No adjacency or synergy bonuses

**Problem**: Buildings don't boost each other; all benefits/costs are global or per-building.  
**Impact**: City layout is strategy-free; no incentive to cluster like with like.  
**Fix**: Green spaces boost adjacent residential land value; transit near workplaces reduce traffic; industry away from residential.  
**Effort**: 2 weeks. **Priority**: Medium.

---

End of PRIORITY_AUDIT.md

