# Group Ironman Tracker Tester Setup

This package runs a local RuneLite client with the plugin preloaded for testing before Plugin Hub release.

## 1. Install Java

Use `Temurin JDK 21` or `Temurin JDK 22` on Windows.

Download:

`https://adoptium.net/temurin/releases/`

Then open Command Prompt and confirm Java is available:

```powershell
java -version
```

## 2. Unzip the package

Extract `gimtracker-tester.zip` to any folder, for example:

```text
C:\RuneLite\gimtracker-tester
```

## 3. Launch the tester build

Inside the extracted folder, double-click:

`launch-plugin.bat`

That launches RuneLite with the Group Ironman Tracker plugin loaded.

You do not need IntelliJ on the tester machine for this package.

## 4. Join the group

Inside RuneLite:

1. Log into the correct account.
2. Open the `Group Ironman Tracker` side panel.
3. Click `Join Group`.
4. Enter the shared `Group Auth Code` you were given.
5. Confirm the group appears and the member list loads.

There is no `API Base URL` setting anymore.

## 5. Suggested test checklist

- Join the shared group from the second machine
- Confirm `Show Group Members` works
- Trigger tracked events on both machines
- Confirm recent group activity updates on both machines
- Leave the group and confirm the panel clears
- Rejoin with the same group auth code

## Troubleshooting

If the backend is unavailable, test this URL in a browser:

```text
http://Group-Ironman-Tracker-env.eba-rmummppd.ap-southeast-2.elasticbeanstalk.com/api/health
```

Expected response:

```json
{"status":"ok"}
```
