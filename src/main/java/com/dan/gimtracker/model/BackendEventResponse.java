package com.dan.gimtracker.model;

public class BackendEventResponse
{
	private long id;
	private String groupCode;
	private String playerName;
	private String eventType;
	private String eventTime;
	private String payloadJson;
	private String createdAt;

	public long getId()
	{
		return id;
	}

	public String getGroupCode()
	{
		return groupCode;
	}

	public String getPlayerName()
	{
		return playerName;
	}

	public String getEventType()
	{
		return eventType;
	}

	public String getEventTime()
	{
		return eventTime;
	}

	public String getPayloadJson()
	{
		return payloadJson;
	}

	public String getCreatedAt()
	{
		return createdAt;
	}
}
