package com.dan.gimtracker.model;

import java.util.List;

public class ProgressUploadRequest
{
	private final String groupCode;
	private final String playerName;
	private final String timestamp;
	private final List<TrackedEvent> events;

	// Wraps queued plugin events with the small amount of metadata the backend needs for Phase 2.
	public ProgressUploadRequest(String groupCode, String playerName, String timestamp, List<TrackedEvent> events)
	{
		this.groupCode = groupCode;
		this.playerName = playerName;
		this.timestamp = timestamp;
		this.events = events;
	}

	// Returns the group identifier associated with the upload.
	public String getGroupCode()
	{
		return groupCode;
	}

	// Returns the in-game player name attached to the upload.
	public String getPlayerName()
	{
		return playerName;
	}

	// Returns the upload timestamp serialized for the backend.
	public String getTimestamp()
	{
		return timestamp;
	}

	// Returns the set of queued tracked events being sent.
	public List<TrackedEvent> getEvents()
	{
		return events;
	}
}
