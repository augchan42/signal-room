# Signal Room producer demo

## Status and scope

The user approved a producer-decision demo on September 22, 2026: three bookings over approximately 10–15 minutes, starting with one complete replayable booking. This document specifies the first booking for review before implementation.

Revision after review: research commitment and the four-second animation production method below are proposed resolutions for final review.

For this demo, this design supersedes the older concept brief's recruitment step and three-song requirement. Use the existing four band members and one original musical theme. The earlier direction remains applicable to the art, audience segments and native Android platform.

## Approach

Build a native Android app in Kotlin and Jetpack Compose, application ID `dev.digitalrain.synthwave`, with one app module. Keep simulation independent of Android. Reuse the rendered control room and broadcast assets; Blender is not a runtime dependency.

A browser prototype would be quicker to share but introduce a second implementation. Live music generation would add network, licensing and response-time dependencies. Neither is part of this milestone.

## First booking: late-night debut

Target duration: 3–5 minutes including listening and results. The player must earn a return invitation and establish an initial niche. No rhythm tapping, live cueing or reaction-time score.

Flow: booking brief → arrangement desk → three preparation slots → confirm broadcast → performance → explained verdict → recap/retry.

The brief reveals a preference for atmosphere and a return-invitation target of 65/100. This is a fictional show's assessment, not an objective measure of musical quality. The number is an initial balancing value. There is no production-budget mechanic in this milestone.

## Decisions

- Arrangement: Sparse, Driving or Layered. All share the same melody and harmony, with clearly different instrumentation and risk profiles. Density is not a difficulty ranking: Sparse exposes the featured member's skill, Driving is sensitive to fatigue, and Layered demands coherence and refinement. None receives an automatic execution bonus for being simpler.
- Featured member: frontman, keyboardist, keytar player or drummer. Member strengths and current fatigue are visible. Each choice selects an audible lead variation and matching stage emphasis.
- Visual concept: restrained monochrome, warm intimate or bright electric. Show an actual matching preview rather than a generic thumbnail.
- Preparation: exactly three slots represented as an unordered multiset, not a sequence. Rehearse improves the featured member's execution but adds fatigue to that member; refine improves coherence without removing all mismatches; rest reduces the featured member's fatigue. Rehearsal/refinement may repeat with diminishing returns. Research occupies at most one slot and follows the immediate commitment rule below.

Arrangement and member selection can be auditioned for free. Non-research preparation is freely edited and applied once when broadcasting, to the final featured member. Changing member does not bank rehearsal or rest for an earlier selection. Changing arrangement updates its public risk description, not a hidden audience forecast or exact score.

Research provides information, not a score bonus. An explicit button says “Spend 1 preparation slot: audience report.” Opening it immediately locks one slot for this attempt; the remaining two slots stay editable. The report reveals fixed audience segment preferences and show risks, independent of arrangement, concept or member. It never evaluates the current plan. The basic show brief and public difficulty remain visible without research. Persist the unlocked report and spent slot immediately.

This deliberately differs from reversible research: hiding information does not remove the player's memory, so a removable research slot would have no reliable cost. A retry restores all slots and lets players use what they learned; this is intentional learning, not a claim of hidden information on repeat attempts.

Fatigue is per member, on a 0–100 scale. Initial values: frontman 40, keyboardist 15, keytar player 25, drummer 30. Effective fatigue is starting fatigue + 12 per rehearsal − 25 per rest, clamped once after summing. Apply the total once to the final featured member, independent of plan order. Other members' fatigue is unchanged in booking 1. Store these values and modifiers in content data. Calibrate sensitivities so rest can improve a fatigued member's result and rehearsal is not always preferable.

There must be competing viable plans, not one strongest arrangement or member. Atmosphere can come from any arrangement; Sparse does not automatically receive the highest brief fit.

## Music and media

Bundle a 20-second original instrumental theme at 120 BPM in 4/4 (a two-bar intro followed by an eight-bar phrase, with a clean ending within 20 seconds). Use authored MIDI/synth material as the initial production route, rendered offline into twelve complete mixes: three arrangements × four featured-member variations. Label it as an instrumental demo; do not claim to implement singing or vocal distribution yet. Frontman emphasis uses the melodic lead as a placeholder, not a simulated vocal performance.

Complete mixes avoid requiring multiple synchronized audio streams in the first app. Switching previews preserves the playback position: capture the current position, fade out over 50 ms, load and seek the replacement, then fade in over 50 ms. One player means a short loading gap is acceptable; overlapping tracks are not. Paused previews stay paused, and completed previews stay at the end until explicitly replayed. The musical position must match within 50 ms on the supported test device; switching is not scored. Later stem mixing is a separate extension.

Every mix must differ audibly in the way its label describes. Deliver AAC-LC stereo at 48 kHz in an M4A container, with lossless WAV production masters retained outside the APK. Target decoded integrated loudness of −14 LUFS ±1 LU, true peak no higher than −1 dBTP, and decoded presentation duration 20.000 seconds ±50 ms (accounting for encoder delay/padding). Keep clean endings. Retain composition/render sources and an asset manifest with provenance. Do not use celebrity recordings, unlicensed covers, voice imitations or paid generation services without separate authorization.

### Minimum visual asset contract

- Three group concept stills: monochrome, warm intimate and bright electric. Use these at the arrangement desk.
- Four member-emphasis stills, one per performer, in a neutral treatment. Use these in member selection and in a labelled featured-member inset during broadcast; they do not pretend to preview each concept.
- Three concept-matched group loops, four seconds each, 24 fps / 96 displayed frames. Broadcast plays the selected concept loop five times under continuous audio, with the selected member inset. No twelve-way video matrix is required.
- If a loop cannot play, use its matching concept still plus the same member inset. Do not replace one concept with another.

Four seconds is two bars at 120 BPM. Produce the loops from Blender: retime body/camera cycles to close at frame 97, but keep beat lighting on a 12-frame period (one beat) and any two-beat pulses on 24 frames. A blanket `setpts=0.8*PTS` would turn the existing 0.5-second light pulses into 0.4-second pulses (150 BPM), so it is not sufficient. Five-to-four-second compression is a 25% speed increase, not 20%. Verify the new loop boundary and beat alignment rather than assuming the old export can be reused unchanged. These are production requirements; this revision does not alter existing renders.

The clip remains illustrative, without lip sync. Audio is the playback clock. Derive visual position from audio time modulo four seconds after pause/resume; do not let separate looping timers accumulate drift. Playback is mandatory on the first attempt at a booking. After one full viewing, show “Skip to verdict” on subsequent attempts. Persist this viewing-completed flag across retries only after the full performance has played; skipping must not alter scoring.

Select only the necessary optimized assets for the APK, not the entire Blender output directory. A missing video falls back as specified above. A missing, corrupt or undecodable bundled audio asset is a build defect: block the booking with a clear content-error screen and diagnostic asset ID, not a retry button or silent fallback. Previously saved state remains intact. Muted play remains available with text descriptions of arrangement differences.

## Judging and audience

Use deterministic authored rules, never a generative model's opinion of the audio. Content metadata must match the actual mixes. The same starting state and choices produce the same result.

Calculate bounded 0–100 dimensions:

- Coherence: arrangement/concept compatibility, lead-role suitability and refinement.
- Execution: member skill, arrangement difficulty, rehearsal and fatigue.
- Brief fit: alignment with the booking's announced preferences.

The invitation score initially uses 35% coherence + 40% execution + 25% brief fit. Return invitation initially requires at least 65. Store weights, threshold, compatibility tables, fatigue values and arrangement sensitivities as validated content in `core/data`, not hardcoded simulation constants. Weights must sum to one. Layered's coherence sensitivity must be documented as an explicit execution interaction so any double contribution is visible in the breakdown. Explain all three contributions and the two largest positive/negative causes. Clamp dimension scores before weighting and round only for display.

Audience response is separate from the invitation. Each of the five existing segments has awareness, affinity and preferences. Fit controls initial interest; coherence and execution can earn affinity even at low initial fit. Momentum affects reach, not artistic quality. The recap may therefore report a lost invitation with a gained niche audience.

Do not penalize freshness in the first booking: there is no prior release. Later bookings may assess repetition against actual release history.

Follow-ups are deferred until booking 2 exists. Booking 1 ends with the explained verdict, audience deltas and retry comparison, not an action whose consequences cannot yet be played. Recap does not claim that stats carry into an unavailable booking.

## State, boundaries and safety

- `core/model`: member, booking, arrangement, concept, preparation plan, audience and result models.
- `core/data`: bundled authored content and asset manifest.
- `core/simulation`: pure validation, scoring and explanation functions.
- `core/audio`: a single lifecycle-aware player for preview and broadcast audio.
- Feature UI: brief, desk, preparation, broadcast, verdict and recap screens.

One state holder owns the current run. Persist a versioned local snapshot at phase transitions and immediately when research is purchased. Confirming broadcast creates one immutable result; repeat taps, rotation and process recreation must not duplicate scoring or rewards. Going to the background pauses playback. Restarting a booking requires confirmation and restores its original conditions. Keep the previous attempt summary for comparison.

No accounts, network permission, analytics, purchases, live APIs or backend are required. No fabricated live votes or AI-listening claims.

## Verification and acceptance

1. A debug APK builds and the full booking runs on an Android emulator offline.
2. All twelve audio previews play and differ as described; switching, pausing and leaving the app do not overlap audio.
3. Preparation cannot exceed three slots; research is charged once on opening and scoring applies exactly once. Permuting non-research actions must leave results unchanged.
4. Unit tests cover deterministic replay, score bounds, fatigue/rehearsal trade-offs, research without a direct bonus, a plan-independent report, research persistence, final-member targeting and separate audience/invitation outcomes.
5. Enumerate all legal first-booking multisets, concepts and members: success and failure must be reachable, each arrangement must support at least one successful plan, and at least one fatigued scenario must benefit from replacing rehearsal with rest. Check for pairwise dominance across matched member/concept/preparation choices, not just whether each option can exceed 65. Test Sparse's skill, Driving's fatigue and Layered's coordination sensitivities directly. Do not claim cross-booking balance until those briefs exist.
6. UI tests cover start-to-recap, retry, rapid confirmation taps, rotation, research commitment, state restoration and first-view/retry skipping.
7. Results identify concrete choices and consequences, not generic praise. A player can explain what to change on retry.
8. Visually inspect Broadcast and control-room screens at phone size, including the corrected keyboard assets.
9. Asset-manifest validation is a build gate: every required ID resolves to an existing decodable file; all twelve mixes meet the duration/loudness/peak tolerances; the three concept stills, four member stills and three loops exist; loops have 96 frames and clean closure. Record measured values, not just declared metadata. Verify audio-position-preserving switching and video/audio alignment after resume.
10. Record playtest feedback on whether the choice was interesting, its sound difference was clear and the verdict felt understandable. Passing automated tests alone does not establish fun.

## Expansion after the first booking

Add Special Stage and Flagship Audition only after the first loop works. Carry audience affinity and fatigue across bookings, then introduce consequential follow-ups between them. Use an original in-game rival's instrumental theme or reinterpretation, not a cover of a real song. Add a final career recap. Recruitment, a full career, additional songs, live generation, singing, detailed choreography and social systems remain deferred.
