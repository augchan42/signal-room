# Synthwave Concept Brief

## Status

This document records the product and art-direction decisions approved on September 22, 2026. The repository is still named `resonance`; it will be renamed separately.

## Product

`Synthwave` is a native Android K-pop agency management game. The player forms and launches a four-member adult male synthwave band in a market that does not initially want its sound.

The goal is not to follow current demand. The player uses strong creative direction to find an initial niche, influence adjacent fan groups, change simulated audience preferences, and climb the chart.

The first playable version is a single-player simulation. It uses simulated fans and rival acts, so it does not require a live community or a three-sided creator, fan, and competitor marketplace. Real-player signals may later affect the simulated market, but they are not required for the initial game.

## First Playable Slice: One Comeback

A run lasts approximately 10–15 minutes:

1. Recruit four members from six candidates. Each candidate has a clear strength, weakness, personality, and starting audience appeal.
2. Choose one of three authored synthwave hooks.
3. Set the comeback's emotional tone, styling, choreography, vocal distribution, and stage emphasis.
4. Spend three schedule slots on training and promotion. Training improves execution, promotion increases awareness, and both affect fatigue.
5. Release the comeback through a short performance sequence assembled from the selected music, visual treatment, member assignments, and execution quality.
6. Review chart position, fan reactions, member popularity, and audience taste changes.
7. Choose one follow-up action: dance challenge, live performance, fan event, or remix.
8. Finish with a career recap showing whether the group failed, established a niche, or moved synthwave into the mainstream.

Coherence is the central rule. Song, styling, choreography, performers, and group identity should support one another. Copying the current trend is not the dominant strategy.

## Hybrid Music System

The initial version uses authored synthwave hooks rather than generating complete songs during play. Player choices alter the perceived version through arrangement variants, vocal distribution, rap or instrumental breaks, choreography, styling, and presentation.

This makes creative choices audible while avoiding generation latency, service dependency, and unresolved music-rights risk. The audio and content interfaces should allow different generation or playback systems later without changing the simulation.

## Audience and Taste Simulation

The market contains five fan segments:

- Performance fans
- Vocal fans
- Fashion and concept fans
- Story and personality fans
- Retro and alternative fans

Each segment tracks awareness, group affinity, and preferences across musical and visual traits. Comebacks use the same trait vocabulary, including retro intensity, accessibility, vocal focus, choreography, styling, and narrative coherence.

Release results use four factors:

- **Fit:** alignment with a segment's current preferences.
- **Execution:** how well the recruited members deliver their assigned parts.
- **Coherence:** how well the creative decisions reinforce one another.
- **Momentum:** existing awareness, loyal fans, and follow-up promotion.

A coherent, well-executed release can change tastes even when its initial fit is low. Retro and alternative fans can adopt the group first. Their response raises awareness among adjacent segments and makes selected synthwave traits more acceptable.

Results explain important causes instead of hiding them in one score. Example messages include `Retro fans adopted the group`, `The vocal arrangement did not suit the lineup`, and `The dance challenge made the chorus accessible`.

## Android Structure

The app uses Kotlin, Jetpack Compose, and a single Gradle module. The planned application ID is `dev.digitalrain.synthwave`.

```text
dev.digitalrain.synthwave
├── core
│   ├── audio
│   ├── data
│   ├── model
│   └── simulation
└── feature
    ├── charts
    ├── comeback
    ├── recruitment
    └── release
```

- `core/model` owns idols, groups, tracks, concepts, fan segments, market state, and comeback results.
- `core/simulation` owns deterministic comeback scoring and taste shifts.
- `core/data` owns authored candidates, hooks, rivals, and scenario content.
- `core/audio` owns playback and mix-variant selection.
- Feature packages own their screens, presentation state, and user actions.

All simulation logic remains independent from Compose. The first version stores game state locally and does not require accounts, a backend, live social features, or a music-generation API.

## Visual Direction: Signal Room

The app uses illustrated management screens and pre-rendered Blender performance clips. Blender is an offline production tool rather than a real-time Android dependency.

Signal Room presents the band in a small retro television studio that grows into a larger broadcast as its audience expands.

### Characters

- Four stylized, non-photorealistic adult male performers.
- Shared body foundation, trousers, and boots.
- Distinct hair and jacket silhouettes: cropped jacket, long vest, oversized blazer, and fitted sleeveless top.
- Detailed facial animation, lip sync, cloth simulation, and realistic celebrity likenesses are excluded from the first pass.

### Stage and rendering

- Shallow platform.
- Rectangular light frames.
- Simple monitor housings.
- Rear screen with animated graphic patterns.
- Ink blue, cream, coral, and electric cyan palette.
- Matte clothing with limited metallic trim.
- Warm face lights and colored rim lights.
- Frontal group shot, alternating medium shots, and slow lateral camera motion.
- Initial character movement is limited to poses, breathing, head turns, and synchronized weight shifts.

Reusable assets include one stage kit, four heads and hair silhouettes, a shared body foundation, four outer garments, three screen patterns, three lighting presets, and three camera setups.

## Blender Concept Workflow

1. Compare Signal Room, Last Train, and Mirror Pop using the same four simple character stand-ins, output size, camera distance, and framing.
2. Develop Signal Room character sheets using front, three-quarter, and silhouette views.
3. Build the stage from modular pieces before adding detail.
4. Render the same performers and camera twice: an intimate warm niche concept and a brighter energetic mainstream concept.
5. Confirm that the difference is legible at 1080 × 1920 portrait resolution on a phone.
6. Produce one five-second test loop before longer performances.

Blender prompts should state production constraints directly: four adult performers, portrait phone output, one modular stage, a shared character foundation, reusable assets, and preference for lighting, materials, poses, and screen graphics over unique geometry.

## Deferred Scope

- Multiple comeback seasons.
- Real-player voting and shared charts.
- Creator subscriptions, payouts, and fan purchases.
- Live generative music services.
- Backend accounts and social systems.
- Real-time 3D rendering on Android.
- Full choreography, facial performance, and lip sync.
- Final brand, store identity, monetization, and Solana or SKR mechanics.

These features are not required to demonstrate the first complete management loop.
