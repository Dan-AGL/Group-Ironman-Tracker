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
		name = "Invite Code",
		description = "Invite code used to join a shared group",
		secret = true
	)
	default String groupCode()
	{
		return "";
	}

	@ConfigItem(
		keyName = "groupName",
		name = "Group Name",
		description = "Display name of the joined group"
	)
	default String groupName()
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
