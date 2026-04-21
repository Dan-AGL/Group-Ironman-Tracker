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
		name = "Group Auth Code",
		description = "Shared group auth code used to join or re-authenticate a group",
		secret = true,
		hidden = true
	)
	default String groupCode()
	{
		return "";
	}

	@ConfigItem(
		keyName = "sessionToken",
		name = "Session Token",
		description = "Session token issued by the backend after authentication",
		secret = true,
		hidden = true
	)
	default String sessionToken()
	{
		return "";
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

	// Sets the minimum drop value before a boss drop is queued for syncing.
	@ConfigItem(
		keyName = "dropValueThreshold",
		name = "Drop Value Threshold",
		description = "Only queue boss drops at or above this coin value"
	)
	default int dropValueThreshold()
	{
		return 2000;
	}
}
