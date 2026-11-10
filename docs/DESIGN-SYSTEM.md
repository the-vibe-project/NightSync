# NightSync design system

Publisher: **The Vibe Project**

## Visual direction
NightSync uses a futuristic, calm, high-trust interface. Avoid noisy sci-fi decoration. The experience should feel premium and precise, not like a game HUD.

- Canvas: deep ink/navy (#070B14).
- Elevated surfaces: navy blue-grey (#101827, #152238).
- Primary action/accent: electric cyan (#69E5FF).
- Secondary accent: soft violet (#A48CFF).
- Text: cool white (#ECF7FF); supporting text: muted blue-grey (#91A7C1).
- Use subtle gradients sparingly for the logo and feature highlights only.
- Keep borders low-contrast, corners consistent, and whitespace generous.

## Platform conventions
- **Android:** Use Material 3 interaction patterns: accessible touch targets, clear labels, bottom navigation for the primary sections, dialogs for confirmations, and system file pickers for upload/download. NightSync colors are applied as a theme, not as a replacement for platform behavior.
- **Windows client:** Use a desktop-first Fluent-inspired layout: stable left navigation, clear page hierarchy, dense but readable file tables, keyboard-friendly controls, and explicit selection states.
- **NightSync Server:** Prioritize at-a-glance health, service state, storage capacity, and guided first-run setup. Advanced destinations and logs stay secondary.

## File manager behavior
- Always show the current folder path and an obvious Back action.
- Separate folders from files and show readable sizes and modification dates.
- Support upload, download, create folder, refresh, and delete.
- Confirm deletion. Non-empty folders must not be removed silently.
- Explain conflicts and size limits in plain language.
- Empty states should direct the user to upload a file or create a folder.

## Figma references (free/community where stated)
These are reference libraries and examples, not runtime dependencies. Before copying any individual asset into NightSync, verify the source file's license and attribution terms.

1. [Google Material 3 Design Kit](https://www.figma.com/community/file/1035203688168086460/material-3-design-kit) — Android component behavior and variants. Listed by Figma as an official UI kit available across plans.
2. [Microsoft Fluent 2 design kits](https://fluent2.microsoft.design/get-started/design) — Windows visual language and desktop component guidance; official web, iOS and Android kits.
3. [Vision UI Dashboard — free version](https://www.figma.com/community/file/1060952013207459371/vision-ui-dashboard-react-mui-dashboard-free-version/) — dark dashboard information hierarchy and panel composition.
4. [Dashboard layout ideas](https://www.figma.com/community/file/1208363071768116045/dashboards-layout-ideas/) — responsive dashboard layout inspiration.

## Implementation rule
Do not blindly clone a template. Reuse patterns that improve clarity and accessibility while keeping NightSync's own component names, brand identity, and code.
