# CodeBihin Messaging

Native Android messaging application built with Kotlin, Jetpack Compose and Firebase.

## Android app

- Package: `in.codebihin.messaging`
- Firebase project: `codebihin-messaging`
- Android application ID: `1:849465329627:android:9d2ddefc4708a9465ac9fe`

## Current milestone

- Email/password registration and sign-in
- Firebase Authentication session persistence
- User profile creation in Cloud Firestore
- FCM device-token registration
- Android notification permission request (Android 13+)
- Firebase Messaging service for incoming push notifications

Chat conversations, calls, media sharing and the separate web admin portal are future milestones.

## Firebase setup

1. Open Firebase project `codebihin-messaging`.
2. Register the Android app with package name `in.codebihin.messaging`.
3. Download `google-services.json`.
4. Place it at `app/google-services.json` (inside the app module).
5. Do not rename the file.
6. In Firebase Authentication, enable the Email/Password provider.
7. Create the Cloud Firestore database.
8. Publish the rules from `firebase/firestore.rules`.
9. Build and run on a real Android device or emulator with Google Play services.

The Google Services Gradle plugin is configured in the root and app Gradle files.

## Build

Open the repository in Android Studio, allow Gradle sync to finish, then run the `app` configuration.

## Repository layout

```
app/                  Android application module
  src/main/           Kotlin source and Android manifest
firebase/             Firestore security rules
build.gradle.kts      Root Gradle plugins
settings.gradle.kts   Gradle project settings
```

## Security notes

- `app/google-services.json` is intentionally excluded from Git.
- Never commit service-account JSON files, private keys or Firebase Admin SDK credentials.
- Firebase client configuration is not an authorization boundary. Protect data with Firestore rules and use a trusted backend for privileged actions.
- FCM tokens are device-specific and should only be used by trusted server-side notification logic.

## Next milestones

1. One-to-one chat using Firestore.
2. Conversation list and unread message counts.
3. Secure Cloud Functions for sending FCM notifications.
4. Group chats, media sharing and call signalling.
5. Separate web admin portal with server-enforced roles.
