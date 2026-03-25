package com.dan.gimtracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("gimtracker")
public interface GIMTrackerConfig extends Config
{
	// Stores the group identifier that the backend uses to associate uploads.
	@ConfigItem(
		keyName = "groupCode",
		name = "Group Code",
		description = "Code used by the backend to associate uploads with your group"
	)
	default String groupCode()
	{
		return "";
	}

	// Points the plugin at the backend that receives progress payloads.
	@ConfigItem(
		keyName = "apiBaseUrl",
		name = "API Base URL",
		description = "Base URL for the backend that receives progress uploads"
	)
	default String apiBaseUrl()
	{
		return "http://localhost:8080";
	}

	// Controls how often queued events should be flushed automatically.
	@ConfigItem(
		keyName = "syncIntervalSeconds",
		name = "Sync Interval",
		description = "How often pending tracked events should be uploaded automatically"
	)
	default int syncIntervalSeconds()
	{
		return 30;
	}

	// Sets the minimum boss KC/completion count before those events are queued for syncing.
	@ConfigItem(
		keyName = "bossKillCountThreshold",
		name = "Boss KC Threshold",
		description = "Only queue boss kill count or completion count events at or above this value"
	)
	default int bossKillCountThreshold()
	{
		return 5;
	}

	// Exposes temporary testing controls so tracked events can be validated without gameplay.
	@ConfigItem(
		keyName = "developerMode",
		name = "Developer Mode",
		description = "Show temporary testing controls in the panel"
	)
	default boolean developerMode()
	{
		return false;
	}
}
