package com.dan.gimtracker.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class TrackedEvent
{
	private final String type;
	private final String timestamp;
	private final String summary;
	private final Map<String, Object> details;

	// Stores one normalized event so different feature types can share the same queue and upload pipeline.
	public TrackedEvent(String type, String timestamp, String summary, Map<String, Object> details)
	{
		this.type = type;
		this.timestamp = timestamp;
		this.summary = summary;
		this.details = details;
	}

	// Creates the first real tracked event type for the plugin: a skill level increase.
	public static TrackedEvent levelUp(String playerName, String skill, int oldLevel, int newLevel)
	{
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("eventId", UUID.randomUUID().toString());
		details.put("playerName", playerName);
		details.put("skill", skill);
		details.put("oldLevel", oldLevel);
		details.put("newLevel", newLevel);
		details.put("levelsGained", newLevel - oldLevel);

		return new TrackedEvent(
			"LEVEL_UP",
			Instant.now().toString(),
			skill + " levelled to " + newLevel,
			details
		);
	}

	// Creates a boss KC or raid completion event once a tracked count crosses the configured threshold.
	public static TrackedEvent bossKillCount(
		String playerName,
		String bossName,
		String countType,
		int sessionCount,
		int totalCount,
		String sessionId
	)
	{
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("eventId", UUID.randomUUID().toString());
		details.put("playerName", playerName);
		details.put("bossName", bossName);
		details.put("countType", countType);
		details.put("count", sessionCount);
		details.put("totalCount", totalCount);
		details.put("sessionId", sessionId);

		return new TrackedEvent(
			"BOSS_KC",
			Instant.now().toString(),
			playerName + " " + bossName + " " + countType.toLowerCase().replace('_', ' ') + ": " + sessionCount,
			details
		);
	}

	public static TrackedEvent bossKillCountSession(
		String playerName,
		String bossName,
		String countType,
		int sessionCount,
		int totalCount,
		String sessionId
	)
	{
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("eventId", UUID.randomUUID().toString());
		details.put("playerName", playerName);
		details.put("bossName", bossName);
		details.put("countType", countType);
		details.put("count", sessionCount);
		details.put("totalCount", totalCount);
		details.put("sessionId", sessionId);

		return new TrackedEvent(
			"BOSS_KC_SESSION",
			Instant.now().toString(),
			playerName + " " + bossName + " session " + countType.toLowerCase().replace('_', ' ') + ": " + sessionCount,
			details
		);
	}

	// Creates a boss drop event with the item, value, and source boss extracted from chat.
	public static TrackedEvent bossDrop(String bossName, String itemName, long value, String playerName, String sourceChannel)
	{
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("eventId", UUID.randomUUID().toString());
		details.put("bossName", bossName);
		details.put("itemName", itemName);
		details.put("value", value);
		details.put("playerName", playerName);
		details.put("sourceChannel", sourceChannel);

		String normalizedBossName = bossName == null ? "" : bossName;
		String summary = normalizedBossName.isBlank()
			? itemName + ": " + value + " gp"
			: itemName + " from " + normalizedBossName + ": " + value + " gp";

		return new TrackedEvent(
			"BOSS_DROP",
			Instant.now().toString(),
			summary,
			details
		);
	}

	// Creates a combat task completion event with the task name and optional tier.
	public static TrackedEvent combatTaskComplete(String playerName, String taskName, String tier, String sourceChannel)
	{
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("eventId", UUID.randomUUID().toString());
		details.put("playerName", playerName);
		details.put("taskName", taskName);
		details.put("tier", tier);
		details.put("sourceChannel", sourceChannel);

		String summary = tier == null || tier.isBlank()
			? "Combat task completed: " + taskName
			: tier + " combat task completed: " + taskName;

		return new TrackedEvent(
			"COMBAT_TASK_COMPLETE",
			Instant.now().toString(),
			summary,
			details
		);
	}

	// Creates a collection-log event when a player unlocks a new item slot.
	public static TrackedEvent collectionLogItem(String playerName, String itemName, int unlockedCount, int totalCount, String sourceChannel)
	{
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("eventId", UUID.randomUUID().toString());
		details.put("playerName", playerName);
		details.put("itemName", itemName);
		details.put("unlockedCount", unlockedCount);
		details.put("totalCount", totalCount);
		details.put("sourceChannel", sourceChannel);

		return new TrackedEvent(
			"COLLECTION_LOG",
			Instant.now().toString(),
			playerName + " unlocked collection log item: " + itemName + " (" + unlockedCount + "/" + totalCount + ")",
			details
		);
	}

	// Returns the event category used by the backend and future filtering.
	public String getType()
	{
		return type;
	}

	// Returns when the event was created.
	public String getTimestamp()
	{
		return timestamp;
	}

	// Returns the short human-readable description shown in the sidebar.
	public String getSummary()
	{
		return summary;
	}

	// Returns the structured event data needed by the backend.
	public Map<String, Object> getDetails()
	{
		return details;
	}
}
