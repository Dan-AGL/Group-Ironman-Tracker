package com.dan.gimtracker.model;

public class BootstrapSessionRequest
{
	private final String inviteCode;
	private final String playerName;

	public BootstrapSessionRequest(String inviteCode, String playerName)
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
