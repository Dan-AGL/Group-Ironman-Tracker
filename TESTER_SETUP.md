# Group Ironman Tracker Tester Setup

This package runs a local RuneLite client with the plugin loaded for testing before Plugin Hub release.

## 1. Install Java

Use `Temurin JDK 21` or `Temurin JDK 22` on Windows.

Download:

`https://adoptium.net/temurin/releases/`

After installation, open a new Command Prompt and check:

```powershell
java -version
```

## 2. Launch the tester build

Double-click:

`launch-plugin.bat`

That starts RuneLite with the Group Ironman Tracker plugin loaded.

## 3. Configure the plugin

Inside RuneLite:

1. Open the `Group Ironman Tracker` plugin config.
2. Confirm `API Base URL` is:

```text
http://Group-Ironman-Tracker-env.eba-rmummppd.ap-southeast-2.elasticbeanstalk.com
```

3. Enter the invite code you were given.
4. Join the group and test normally.

## 4. Suggested test checklist

- Join the shared group
- Trigger tracked events
- Confirm recent group activity updates
- Close and reopen RuneLite
- Confirm history persists for the same invite code

## Troubleshooting

If the backend is unavailable, test this URL in a browser:

```text
http://Group-Ironman-Tracker-env.eba-rmummppd.ap-southeast-2.elasticbeanstalk.com/api/health
```

Expected response:

```json
{"status":"ok"}
```
