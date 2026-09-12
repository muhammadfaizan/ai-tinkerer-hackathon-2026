# Nudge Coach mobile app

Expo-managed React Native app for demonstrating goal-aware nudges from `apps/server`.

## Run

```bash
cd apps/mobile
cp .env.example .env
# Replace the example IP with your computer's LAN IP address.
npx expo start
```

Set `EXPO_PUBLIC_BACKEND_URL` in `.env`, for example `http://192.168.1.42:3000`. Do **not** use `localhost` on a physical device: it refers to the phone itself. Your phone and development machine must be connected to the same local network, and the Express backend must be running on port 3000.

The app saves goals in AsyncStorage under `userGoals`, sends simulated activities to `POST /nudge`, and keeps its nudge history only for the current session.

## In-app nudges

When the backend returns `shouldNotify: true`, the nudge appears immediately as an animated in-app card. No system notification permission, development build, or custom native setup is needed; the app runs directly in Expo Go.
