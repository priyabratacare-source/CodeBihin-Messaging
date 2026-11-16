# CodeBihin Messaging — Product & Engineering Implementation Plan

**Target:** A polished WhatsApp-style Android messaging app built on the existing Kotlin + Jetpack Compose + Firebase project.

**Status:** Planning baseline committed. This document is a delivery plan, not a claim that every listed feature is already implemented.

## Existing baseline

- Firebase Email/Password authentication.
- Firestore-backed one-to-one and group conversations.
- Realtime message listeners.
- FCM token registration and Android notification display.
- Admin web portal and Firestore admin authorization rules.
- Firestore-triggered FCM Cloud Functions in `functions/` (deployment still required).
- GitHub Actions debug APK build.
- Current call action creates an external Jitsi link; native in-app WebRTC is not yet implemented.

## Delivery principles

1. Keep the current Firebase project and Android package `in.codebihin.messaging`.
2. Deliver small, buildable milestones; run the Android Actions workflow after each Android change.
3. Never weaken Firestore rules to make a screen work. Add rules and tests with each new collection.
4. Do not store passwords, private keys, service-account JSON, or plaintext encryption keys in Firestore or Git.
5. Do not describe a feature as production-ready until it has been tested on at least two real devices.
6. Treat offline behaviour, reconnects, duplicate events, and notification permission denial as normal cases.

## Phase 1 — Messaging foundation

### Product work
- Refine the app shell, navigation, chat list, profile, settings, empty states, loading states and error states.
- Improve conversation UI with message grouping, timestamps, reply previews, reactions, edit/delete, forwarding, read receipts and typing indicators.
- Add paginated message history and robust attachment selection/preview.
- Add user presence as best-effort (online/last-seen), with privacy settings.
- Keep existing one-to-one and group chat flows working during migration.

### Data direction
- Keep `chats/{chatId}` and `chats/{chatId}/messages/{messageId}`.
- Extend message schema with explicit `type`, `text`, `attachment metadata`, `replyTo`, `editedAt`, `deletedAt`, `createdAt`, `senderId`, and per-user receipt metadata where appropriate.
- Use server timestamps and stable message IDs; avoid unbounded arrays in chat documents.

### Acceptance
- Messages remain correctly ordered across pagination and reconnect.
- A user cannot edit/delete another user's message unless an explicitly defined moderator policy permits it.
- Firestore rules validate field types, membership and allowed field changes.

## Phase 2 — Groups and channels

### Product work
- Group creation, group avatar/about, member invitations, roles (owner/admin/member), join/leave, remove member and group settings.
- Channel creation, public/private visibility, followers/subscribers, channel owner/admin roles and broadcast posts.
- Invite links must be revocable and rate-limited; private channels must not leak membership or posts.

### Data direction
- Continue to use chat documents for group conversations.
- Introduce a separate `channels/{channelId}` collection with controlled metadata and a `posts/{postId}` subcollection.
- Store membership/subscription records in subcollections for scalable membership rather than ever-growing arrays.
- Use transactions or trusted server functions for role changes, invitation redemption and counters.

### Acceptance
- Only group admins can change group settings or membership.
- Only channel admins can publish; only authorized users can read private channel content.
- Leaving/removing a member revokes access immediately through rules.

## Phase 3 — Native voice and video calls

### Product work
- Native WebRTC voice/video media, incoming call screen, ringing/accepted/rejected/ended states, mute, speaker, camera switch, camera off and call history.
- Call notifications and missed-call notifications.
- Handle app backgrounding, permission denial, network changes and call cleanup.

### Signalling/data direction
- `calls/{callId}`: caller UID, callee UID, conversation ID, media type, state, created/updated timestamps, offer/answer SDP metadata.
- `calls/{callId}/callerCandidates/{candidateId}` and `calleeCandidates/{candidateId}`: ICE candidates.
- Firestore is signalling/control-plane only; audio/video media flows through WebRTC.
- Add participant-only Firestore rules and strict state-transition validation.
- Configure STUN and a TURN relay. STUN-only connectivity is not reliable on every carrier, NAT or restrictive network.

### Acceptance
- Two physical Android devices can complete voice and video calls on different networks.
- Test accept/reject, timeout, busy, hang-up, app background, permission denial, Wi-Fi/mobile handoff and reconnect.
- No non-participant can read or alter call signalling documents.
- Calls release camera, microphone, peer connections and listeners on every terminal path.

## Phase 4 — Push notifications

### Product work
- Notifications for direct/group messages, calls, missed calls, channel posts and admin announcements.
- Notification preferences, channel importance/sound, deep links into the correct conversation and safe lock-screen previews.
- Token refresh, multi-device token storage and stale-token cleanup.

### Delivery
- Retain Android FCM registration and the existing messaging service.
- Use trusted Cloud Functions/Admin SDK for background push delivery; never expose server credentials in the Android app.
- Cloud Functions deployment currently requires the Firebase Blaze plan for this project's second-generation functions. Review billing, quotas and budget alerts before enabling.
- Android 13+ notification permission is runtime-controlled; the app must continue to work if permission is denied.

### Acceptance
- Verify foreground, background and terminated-app delivery on real devices.
- Confirm duplicate event handling, stale token cleanup and notification deep links.
- Do not send private message plaintext in push payloads when end-to-end encryption is enabled.

## Phase 5 — End-to-end encryption and cloud backup

### Encryption design
- Use a reviewed, established protocol/library (for example, a Signal Protocol implementation) rather than inventing cryptography.
- Define identity keys, signed prekeys, one-time prekeys, session establishment, key rotation, device linking and safety-number/key-change UX.
- Encrypt message bodies and sensitive attachment content on-device before upload.
- Keep server-side moderation limitations explicit: the server cannot inspect encrypted message contents.
- Multi-device support requires a separate key per device and secure device enrollment.

### Backup design
- Encrypted client-side backup; encryption key/passphrase must not be stored beside the backup.
- Store backup metadata, version, size, timestamp and integrity hash separately from encrypted backup bytes.
- Restore only after user authentication and local decryption-key recovery.
- Provide delete/export controls and retention policy. Never claim a backup is recoverable until restore is tested.

### Acceptance
- Verify ciphertext is stored (not plaintext) for encrypted chats.
- Test new-device restore, wrong backup key, corrupted backup, key rotation and account recovery.
- Complete independent security review before marketing E2EE as production-grade.

## Phase 6 — Advanced admin controls

- User search, status and account actions; block/disable and force-logout controls.
- Announcements and targeted notices; audit trail for every privileged action.
- Reports/abuse workflow with role-based access and retention rules.
- Aggregate operational metrics that avoid exposing private message content.
- Maintenance mode and incident/status messaging.
- Admin access remains enforced by Firestore rules and trusted backend checks, not only by hiding UI elements.
- Clearly document that client-observed force logout takes effect when the device receives the Firestore update and is online; immediate server-side revocation requires trusted backend/Auth session controls.

## Cross-cutting security and quality

- Add emulator-based Firestore rules tests for owner/member/non-member/admin cases.
- Add input validation, abuse/rate limits, pagination limits and safe error handling.
- Add privacy policy, account deletion and data export workflows.
- Add crash/error reporting only after privacy review and consent/configuration.
- Use a release signing process and protect signing secrets in GitHub Actions secrets; never commit keystores.
- Keep debug builds separate from signed production release builds.

## Required external setup / cost checks

- **Firebase Spark:** Authentication and Firestore can be used within their free quotas; monitor quotas.
- **Cloud Functions:** Current functions use second-generation triggers and require Blaze billing. Set budget alerts and review pricing before deployment.
- **Cloud Storage:** Needed for real media/backup file storage; confirm billing/region/rules before enabling.
- **TURN:** A reliable TURN service may have usage charges. Choose a provider and configure credentials outside source control.
- **E2EE:** Choose and review a maintained Android-compatible protocol library before implementing encrypted messaging.

## Milestone gates

A milestone is complete only when:
1. Code is committed to `main`.
2. GitHub Actions Android build succeeds.
3. Firestore rules are reviewed and deployed separately where required.
4. Manual test checklist is completed on real devices for device/network features.
5. README and release notes accurately describe shipped versus planned functionality.
