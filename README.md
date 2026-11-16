# CodeBihin Messaging

Native Android messaging app built with Kotlin, Jetpack Compose and Firebase.

## Firebase project

- Project ID: `codebihin-messaging`
- Android package: `in.codebihin.messaging`
- Android Firebase App ID: `1:849465329627:android:9d2ddefc4708a9465ac9fe`

## Android features in this milestone

- Email/password registration and sign-in with Firebase Authentication.
- Public profile directory (display name only; email addresses are not exposed in public profiles).
- One-to-one conversations using Firestore.
- Group creation and group messaging, with creator assigned as group administrator.
- Public channels with owner/admin publishing and a live channel feed.
- Official announcements feed inside the app.
- Message replies, sender edit/delete actions, and participant read receipts.
- Real-time chat, channel and announcement listeners.
- Hosted file / Google Drive / other HTTPS link sharing. Files are not uploaded to Firebase Storage.
- FCM device-token registration, Android notification permission prompt and notification display.
- Existing Jitsi meeting invitation link remains the current call experience.

### Calling limitation

The Call action currently creates a unique Jitsi meeting link, posts it into the conversation, and opens the meeting externally. This is not an embedded native WebRTC calling client. Firestore participant-only call-signalling rules are now a foundation only; a native WebRTC media engine, incoming-call UI, TURN configuration and device testing are still required.

## GitHub Actions APK build

Workflow: `.github/workflows/android-apk.yml`

It builds a debug APK on pushes to `main`, pull requests, or manual dispatch. After a successful run:

1. Open the repository's **Actions** tab.
2. Open the latest **Build Android APK** run.
3. Download the `CodeBihin-Messaging-debug` artifact.
4. Extract it to find `app-debug.apk`.

The Firebase Android config file is currently present at `app/google-services.json`. It is a client configuration, not a service-account private key. Never commit Firebase Admin service-account keys or other server secrets.

## Firestore setup

1. Enable Email/Password under Firebase Authentication.
2. Create the Cloud Firestore database.
3. Publish `firebase/firestore.rules` (or deploy with Firebase CLI).
4. Install and test the Android APK on a real device.

## Push notifications

The Android app registers each signed-in device's FCM token under `users/{uid}.fcmTokens`.

The `functions/` directory contains Cloud Functions that send notifications:
- New message -> notify the other chat members.
- New admin announcement -> notify registered users.

Deploying Cloud Functions requires the Firebase project to use the **Blaze (pay-as-you-go) plan**. Usage may incur charges. Review quotas and billing before deployment.

Install Firebase CLI and deploy from the repository root:

```bash
npm install -g firebase-tools
firebase login
cd functions
npm install
cd ..
firebase deploy --only firestore:rules,functions
```

## Admin web portal

Portal source: `admin/index.html`

Firebase Hosting is configured to publish the `admin/` folder. Before using it:

1. In Firebase Console, register a separate **Web app** in this same project.
2. Copy the Web app's Firebase configuration.
3. Replace the configuration in `admin/index.html`. In particular, replace `PASTE_FIREBASE_WEB_APP_ID_HERE` with the Web App ID. Use the Web app's own `appId`, not the Android App ID.
4. Enable Email/Password Authentication.
5. Create an admin account in Authentication.
6. In Firestore, create `admins/{ADMIN_AUTH_UID}` with fields `active: true` and `role: "admin"`. Create this document manually as project owner; clients cannot grant themselves admin access.
7. Deploy hosting:

```bash
firebase deploy --only hosting
```

The admin portal provides user/conversation overview and announcement publishing. Announcement push delivery works after the Cloud Functions are deployed. Admin authorization is enforced by Firestore rules, not merely by hiding the page.

## Repository structure

```
app/                    Android app
admin/                  Firebase-hosted admin portal
firebase/firestore.rules Firestore security rules
functions/              FCM notification Cloud Functions
.github/workflows/      GitHub Actions APK build
firebase.json           Firebase deployment configuration
.firebaserc             Default Firebase project
```


## Product roadmap

The phased product and engineering plan for WhatsApp-style messaging, groups/channels, native WebRTC calls, push notifications, end-to-end encryption, encrypted cloud backup and advanced administration is documented in [`docs/IMPLEMENTATION_PLAN.md`](docs/IMPLEMENTATION_PLAN.md). These are planned milestones; consult the current feature list and Actions build before treating a feature as shipped.

## Important production work remaining

- Embedded voice/video calling with authenticated WebRTC, TURN and call lifecycle handling.
- Typing indicators, online status and paginated message history.
- Native WebRTC calling (current call button still uses external Jitsi).
- End-to-end encryption and encrypted cloud backup (not yet implemented).
- Message moderation/reporting and admin audit logs.
- Stronger abuse prevention, rate limiting, account deletion and privacy policy.
- Release signing and Play Store readiness (the Actions workflow currently builds a debug APK).
