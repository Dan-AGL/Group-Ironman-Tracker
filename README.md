# Group Ironman Tracker
A RuneLite plugin for tracking Group Ironman activity.

## Backend Integration

The plugin posts batched progress payloads to `POST /api/progress`.

The deployed Spring backend is available at `http://Group-Ironman-Tracker-env.eba-rmummppd.ap-southeast-2.elasticbeanstalk.com`.

Health checks work at:

```text
http://Group-Ironman-Tracker-env.eba-rmummppd.ap-southeast-2.elasticbeanstalk.com/health
http://Group-Ironman-Tracker-env.eba-rmummppd.ap-southeast-2.elasticbeanstalk.com/api/health
```

Set the plugin `API Base URL` to `http://Group-Ironman-Tracker-env.eba-rmummppd.ap-southeast-2.elasticbeanstalk.com`.

## Mock Backend

For local payload inspection without PostgreSQL or Spring Boot:

```powershell
.\gradlew.bat runMockBackend
```

The mock backend accepts the same base URL and prints the JSON body it receives.
