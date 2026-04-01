# Group Ironman Tracker
A RuneLite plugin for tracking Group Ironman activity.

## Backend Integration

The plugin posts batched progress payloads to `POST /api/progress`.

The real Spring backend in the sibling `gimtrackerbackend` repository now listens on `http://localhost:8080`.

Health checks work at:

```text
http://localhost:8080/health
http://localhost:8080/api/health
```

Set the plugin `API Base URL` to `http://localhost:8080`.

## Mock Backend

For local payload inspection without PostgreSQL or Spring Boot:

```powershell
.\gradlew.bat runMockBackend
```

The mock backend accepts the same base URL and prints the JSON body it receives.
