# Signal Room producer demo

## Status and scope

The user approved a producer-decision demo on September 22, 2026: three bookings over approximately 10–15 minutes, starting with one complete replayable booking. This document specifies the first booking for review before implementation.

For this demo, this design supersedes the older concept brief's recruitment step and three-song requirement. Use the existing four band members and one original musical theme. The earlier direction remains applicable to the art, audience segments and native Android platform.

## Approach

Build a native Android app in Kotlin and Jetpack Compose, application ID `dev.digitalrain.synthwave`, with one app module. Keep simulation independent of Android. Reuse the rendered control room and broadcast assets; Blender is not a runtime dependency.

A browser prototype would be quicker to share but introduce a second implementation. Live music generation would add network, licensing and response-time dependencies. Neither is part of this milestone.

## First booking: late-night debut

Target duration: 3–5 minutes including listening and results. The player must earn a return invitation and establish an initial niche. No rhythm tapping, live cueing or reaction-time score.

Flow: booking brief → arrangement desk → three preparation slots → confirm broadcast → performance → explained verdict → follow-up → recap/retry.

The brief reveals a preference for atmosphere, a limited production budget and a return-invitation target of 65/100. This is a fictional show's assessment, not an objective measure of musical quality. The number is an initial balancing value.

## Decisions

- Arrangement: Sparse, Driving or Layered. All share the same melody and harmony, with clearly different instrumentation, density and difficulty.
- Featured member: frontman, keyboardist, keytar player or drummer. Member strengths and current fatigue are visible. Each choice selects an audible lead variation and matching stage emphasis.
- Visual concept: restrained monochrome, warm intimate or bright electric. Show an actual matching preview rather than a generic thumbnail.
- Preparation: exactly three slots. Rehearse improves execution but adds fatigue; refine improves coherence without removing all mismatches; research reveals the audience forecast; rest reduces fatigue. Actions may repeat, with diminishing rehearsal/refinement returns.

Arrangement and member selection can be auditioned for free before preparation is committed. Previewing never changes game state or consumes a slot. Preparation is selected as a plan, freely edited, then applied once when broadcasting. Changing the arrangement before confirmation recalculates its forecast and difficulty.

Research provides information, not a hidden score bonus. Its forecast describes likely segment responses and major risks without exposing an exact winning recipe. There must be competing viable plans, not one strongest arrangement or member.

## Music and media

Bundle a 20-second original instrumental theme at 120 BPM in 4/4 (ten bars). Use authored MIDI/synth material as the initial production route, rendered offline into twelve complete mixes: three arrangements × four featured-member variations. Label it as an instrumental demo; do not claim to implement singing or vocal distribution yet. Frontman emphasis uses the melodic lead as a placeholder, not a simulated vocal performance.

Complete mixes avoid requiring multiple synchronized audio streams in the first app. Switching previews restarts the same excerpt with a short fade; switching is not scored. Later stem mixing is a separate extension.

Every mix must differ audibly in the way its label describes. Keep comparable perceived loudness, the same duration, and clean endings. Retain composition/render sources and an asset manifest with provenance. Do not use celebrity recordings, unlicensed covers, voice imitations or paid generation services without separate authorization.

Use the existing five-second visual loop repeated four times where appropriate, plus member stills and alternate camera renders. The clip is illustrative, without lip sync. The existing five-second loop is not a whole-bar loop at 120 BPM; do not promise bar-synchronous choreography or seamless musical phrasing from the video. Audio plays continuously for the full excerpt and is the playback clock.

Select only the necessary optimized assets for the APK, not the entire Blender output directory. A missing video falls back to the appropriate still. Missing audio shows an error and retry option; a silent placeholder does not satisfy completion. Muted play remains available with text descriptions of arrangement differences.

## Judging and audience

Use deterministic authored rules, never a generative model's opinion of the audio. Content metadata must match the actual mixes. The same starting state and choices produce the same result.

Calculate bounded 0–100 dimensions:

- Coherence: arrangement/concept compatibility, lead-role suitability and refinement.
- Execution: member skill, arrangement difficulty, rehearsal and fatigue.
- Brief fit: alignment with the booking's announced preferences.

The invitation score is 35% coherence + 40% execution + 25% brief fit. Return invitation requires at least 65. Explain all three contributions and the two largest positive/negative causes. Clamp dimension scores before weighting and round only for display.

Audience response is separate from the invitation. Each of the five existing segments has awareness, affinity and preferences. Fit controls initial interest; coherence and execution can earn affinity even at low initial fit. Momentum affects reach, not artistic quality. The recap may therefore report a lost invitation with a gained niche audience.

Do not penalize freshness in the first booking: there is no prior release. Later bookings may assess repetition against actual release history.

After results, choose one follow-up: a behind-the-scenes segment raises story/personality awareness, an instrumental feature raises retro/alternative awareness, or rest lowers fatigue. Show exact before/after changes and apply once. These choices are simulated; nothing is posted externally.

## State, boundaries and safety

- `core/model`: member, booking, arrangement, concept, preparation plan, audience and result models.
- `core/data`: bundled authored content and asset manifest.
- `core/simulation`: pure validation, scoring, explanation and follow-up functions.
- `core/audio`: a single lifecycle-aware player for preview and broadcast audio.
- Feature UI: brief, desk, preparation, broadcast, verdict and recap screens.

One state holder owns the current run. Persist a versioned local snapshot at phase transitions. Confirming broadcast creates one immutable result; repeat taps, rotation and process recreation must not duplicate scoring or rewards. Going to the background pauses playback. Restarting a booking requires confirmation and restores its original conditions. Keep the previous attempt summary for comparison.

No accounts, network permission, analytics, purchases, live APIs or backend are required. No fabricated live votes or AI-listening claims.

## Verification and acceptance

1. A debug APK builds and the full booking runs on an Android emulator offline.
2. All twelve audio previews play and differ as described; switching, pausing and leaving the app do not overlap audio.
3. Preparation cannot exceed three slots; scoring and follow-up apply exactly once.
4. Unit tests cover deterministic replay, score bounds, fatigue/rehearsal trade-offs, research without a direct bonus and separate audience/invitation outcomes.
5. Enumerate legal first-booking plans to check that both invitation success and failure are reachable, multiple approaches can succeed, and no arrangement/member wins universally across the planned briefs.
6. UI tests cover start-to-recap, retry, rapid confirmation taps, rotation and state restoration.
7. Results identify concrete choices and consequences, not generic praise. A player can explain what to change on retry.
8. Visually inspect Broadcast and control-room screens at phone size, including the corrected keyboard assets.
9. Record playtest feedback on whether the choice was interesting, its sound difference was clear and the verdict felt understandable. Passing automated tests alone does not establish fun.

## Expansion after the first booking

Add Special Stage and Flagship Audition only after the first loop works. Carry audience affinity and fatigue across bookings. Use an original in-game rival's instrumental theme or reinterpretation, not a cover of a real song. Add a final career recap. Recruitment, a full career, additional songs, live generation, singing, detailed choreography and social systems remain deferred.
