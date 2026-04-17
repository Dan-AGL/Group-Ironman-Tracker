package com.dan.gimtracker.model;

public class AuthenticatedGroupResponse
{
	private long id;
	private String name;
	private String inviteCode;
	private String createdBy;
	private String playerName;
	private String role;
	private String sessionToken;

	public long getId()
	{
		return id;
	}

	public String getName()
	{
		return name;
	}

	public String getInviteCode()
	{
		return inviteCode;
	}

	public String getCreatedBy()
	{
		return createdBy;
	}

	public String getPlayerName()
	{
		return playerName;
	}

	public String getRole()
	{
		return role;
	}

	public String getSessionToken()
	{
		return sessionToken;
	}
}
