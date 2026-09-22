# Resonance Android Scaffold Design

## Purpose

Create a buildable native Android foundation for the CLOCK IN Solana Mobile Hackathon. Resonance is a working codename. The initial product loop is:

1. Read a shared daily situation.
2. Choose a perspective without being scored right or wrong.
3. See how other participants responded.
4. Compare those responses with an I Ching framing.
5. Enter a conversation about the situation.

The scaffold establishes technical boundaries for that loop without committing to final product copy, visual identity, backend infrastructure, or token mechanics.

## Project Identity

- Project name: `Resonance`
- Android application ID and namespace: `dev.digitalrain.resonance`
- The application ID may be changed before any public store release if the final product name changes.
- Native Android application written in Kotlin.
- Jetpack Compose and Material 3 for UI.
- Minimum SDK 28 and target/compile SDK 36, matching the supported baseline in `sixlines-android`.

## Build Structure

Begin with one `app` Gradle module. Feature boundaries are represented by packages rather than separate Gradle modules because the product model and dependencies are still changing.

Use a Gradle version catalog and Kotlin DSL. Include a Gradle wrapper, standard Android resource structure, lint configuration, release shrinking rules, and repository documentation.

The package structure is:

```text
dev.digitalrain.resonance
├── app
├── core
│   ├── data
│   ├── model
│   └── ui
└── feature
    ├── conversation
    ├── experiment
    ├── garden
    ├── reveal
    └── situation
```

`core/model` contains product concepts independent of Android UI. `core/data` defines repository interfaces and local implementations. `core/ui` contains shared Compose components and theme code. Each feature package owns its screen, state, and presentation logic.

## Initial User Flow

The runnable scaffold demonstrates the complete product loop with local sample data:

```text
Today's Situation → Choose Perspective → Reveal → Conversation
```

The situation screen presents direct language and does not require users to understand the Garden. The reveal screen shows an aggregate response distribution and an I Ching framing, followed by a fit response of yes, partly, or no. The conversation screen contains a local sample thread and a non-persistent composer.

The Garden remains a visual and navigational shell. It does not introduce invented labels for standard actions. The experiment package is present as an isolated boundary but is not part of the default navigation flow.

## State and Data Flow

A repository interface exposes the current situation, its perspectives, aggregate response data, I Ching framing, and conversation messages. The initial repository is an in-memory implementation with deterministic sample content.

Screens render immutable UI state and emit user actions to screen-level state holders. Navigation passes stable identifiers rather than complete model objects. No network, database, wallet, or account dependency is required to run the scaffold.

Solana Mobile and SKR support are represented only by a documented integration boundary. Wallet connection, transaction signing, rewards, staking, and token access are excluded until the product chooses a concrete on-chain behavior.

## Error Handling

The local repository returns explicit result types so later remote implementations can represent loading, empty, and failure states without changing feature interfaces. The scaffold includes visible loading, empty, and retry states where data is consumed.

User selections remain in memory during the process lifetime. The UI prevents duplicate submission while an action is in progress. No sample action performs a real transaction or external write.

## Testing and Verification

The scaffold includes:

- Unit tests for perspective selection and reveal-state transitions.
- Repository tests for deterministic sample content.
- A Compose navigation test covering Situation → Reveal → Conversation.
- Gradle commands for unit tests, lint, and debug APK assembly.

Completion requires all tests to pass and a debug APK to build with the documented JDK and Android SDK configuration.

## Explicitly Deferred

- Final product name and branding.
- Production visual design and Garden artwork.
- Backend, authentication, moderation, analytics, and notifications.
- Live social discussion and aggregate calculations.
- Mobile Wallet Adapter and Seed Vault integration.
- SKR rewards, access, staking, purchases, or other token mechanics.
- Research cohort assignment, experimental stimuli, and study data collection.
- Solana dApp Store signing and submission metadata.

These items require separate product and security decisions and are not necessary for a reliable Android scaffold.
