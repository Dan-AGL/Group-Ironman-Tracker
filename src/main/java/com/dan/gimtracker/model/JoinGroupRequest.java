package com.dan.gimtracker.model;

public class JoinGroupRequest
{
	private final String inviteCode;
	private final String playerName;

	public JoinGroupRequest(String inviteCode, String playerName)
	{
		this.inviteCode = inviteCode;
		this.playerName = playerName;
	}

	public String getInviteCode()
	{
		return inviteCode;
	}

	public String getPlayerName()
	{
		return playerName;
	}
}
