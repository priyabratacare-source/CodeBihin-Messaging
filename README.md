# CodeBihin Messaging

A Firebase-based Android messaging application with a separate web administration portal.

## Planned modules

- Android registration and login with persistent Firebase Authentication sessions
- User approval and account status management
- One-to-one messaging and push notifications
- Voice and video calling (WebRTC)
- Separate web-based administrator portal
- Server-side administrative actions using Firebase Cloud Functions
- Firestore security rules

## Project structure

```
android-app/   Android application
admin-panel/   Separate web administrator portal
functions/     Trusted server-side Firebase functions
firebase/      Firebase configuration and security rules
```

## Security

Administrative privileges must be enforced server-side with Firebase Authentication custom claims. Never store plaintext passwords or expose Firebase Admin SDK credentials in the Android app or browser. Firestore rules must deny access by default and grant only the minimum required access.

## Setup status

Initial repository documentation. Firebase project configuration and application modules still need to be added.
