# Design System

Phase 2 introduces a small usable design system for the LuckyBox MVP.

## Routes

- `/`: storefront home with shared header, footer, campaign cards, progress, loading skeleton, and error state.
- `/kuji/:slug`: campaign detail with metrics, prize rows, policy panels, and image fallback behavior.
- `/design-system`: visual inventory for buttons, badges, cards, tabs, form controls, progress, empty state, error state, toast, modal, responsive grid, and image fallback.

## Layout

- Desktop and tablet use the sticky top header.
- Mobile hides the top header and uses a fixed bottom navigation.
- Footer is present across app routes.
- Containers use fixed responsive constraints and grid children set `min-width: 0` to prevent mobile overflow.

## Component Conventions

- Buttons, badges, tabs, modal, toast, form controls, and progress use Bootstrap primitives with LuckyBox styling overrides.
- Cards use 8px radius, stable image aspect ratios, and consistent body spacing.
- Empty and error states are full-width panels that can be reused in later account, wallet, shipment, and admin pages.
- Images should keep a fixed aspect ratio and apply `.image-fallback` when an asset fails to load.

## Verification

Phase 2 was verified at:

- Desktop: 1440px
- Tablet: 768px
- Mobile: 390px

Checks include no horizontal overflow, no detected text overflow, visible footer, correct mobile bottom navigation behavior, and a working Bootstrap modal.
