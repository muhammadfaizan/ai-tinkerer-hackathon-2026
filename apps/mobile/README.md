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

The app saves goals in AsyncStorage under `userGoals`, sends activities to `POST /nudge`, and keeps its nudge history only for the current session.

## In-app nudges

When the backend returns `shouldNotify: true`, the nudge appears immediately as an animated in-app card. There are no system notifications.

## Live Android usage tracking

Demo Mode works in Expo Go. Live usage tracking needs the Android development build because it includes a small local native module that reads Android Usage Stats.

```bash
cd apps/mobile
npx eas build --profile development --platform android
```

Install the build on an Android device, then open the app and select **Grant Usage Access**. Android opens its Usage Access settings page; enable access for **Nudge Coach**, then return to the app. This is special Android access, so it cannot be requested with a normal pop-up permission dialog.

For local JavaScript changes after installing the development build, start Metro with:

```bash
npx expo start --dev-client
```

While Nudge Coach is open, it polls the last 30 minutes of activity every minute. If a different app was used for at least one minute, it sends that recent activity to `/nudge`. For a demo, use another app for a minute and return to Nudge Coach; the latest session is then checked. The existing Demo Mode buttons remain available as a reliable fallback.
