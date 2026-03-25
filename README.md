# Group Ironman Tracker
A RuneLite plugin for tracking Group Ironman activity.

## Local Phase 2 Testing

Start the mock backend:

```powershell
.\gradlew.bat runMockBackend
```

Check that it is running:

```text
http://localhost:8080/health
```

Then point the plugin `API Base URL` to:

```text
http://localhost:8080
```

When you click `Sync Now`, the mock backend will print the JSON payload it receives from the plugin.
