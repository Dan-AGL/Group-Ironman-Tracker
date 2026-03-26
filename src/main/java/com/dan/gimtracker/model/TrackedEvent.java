package com.dan.gimtracker.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

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
	public static TrackedEvent levelUp(String skill, int oldLevel, int newLevel)
	{
		Map<String, Object> details = new LinkedHashMap<>();
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

	// Creates a fake level-up event for developer testing without needing a real level-up.
	public static TrackedEvent testLevelUp()
	{
		return levelUp("ATTACK", 98, 99);
	}

	// Creates a boss KC or raid completion event once a tracked count crosses the configured threshold.
	public static TrackedEvent bossKillCount(String bossName, String countType, int count)
	{
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("bossName", bossName);
		details.put("countType", countType);
		details.put("count", count);

		return new TrackedEvent(
			"BOSS_KC",
			Instant.now().toString(),
			bossName + " " + countType.toLowerCase().replace('_', ' ') + ": " + count,
			details
		);
	}

	// Creates a boss drop event with the item, value, and source boss extracted from chat.
	public static TrackedEvent bossDrop(String bossName, String itemName, long value, String sourceChannel)
	{
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("bossName", bossName);
		details.put("itemName", itemName);
		details.put("value", value);
		details.put("sourceChannel", sourceChannel);

		return new TrackedEvent(
			"BOSS_DROP",
			Instant.now().toString(),
			itemName + " from " + bossName + ": " + value + " gp",
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
