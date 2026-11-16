# Mobile UI prototype

Interactive HTML design target for the CodeBihin Android app.

## Open the prototype

Open [`web/mobile-ui-prototype.html`](../web/mobile-ui-prototype.html) in a modern browser.

Requires network access for Tailwind CDN and Google Fonts (Plus Jakarta Sans, JetBrains Mono).

## Screens covered

- Studio switcher: Chat Dashboard, Sign In, Create Account
- Light / dark theme
- Chat list with search, filter chips, online strip, FAB, delete chat
- Conversation view with replies, reactions, delete message, emoji drawer, code snippets, live location share
- Status tab + full-screen status viewer + add status composer
- Communities with channel shortcuts
- Encrypted calls overlay (demo)
- Profile, edit profile (hosted photo URL), blocked contacts, support/legal sub-pages
- CBS-256 encryption verification modal (demo branding)

## Demo auth

Any valid-looking email and password of at least 6 characters unlocks the dashboard. State is local to the browser only.

## Relationship to Android

This file is the **visual and interaction reference** for Jetpack Compose work in `app/`.
Compose implementation tracks this design in phases (shell, chat list, conversation, tabs, profile).
