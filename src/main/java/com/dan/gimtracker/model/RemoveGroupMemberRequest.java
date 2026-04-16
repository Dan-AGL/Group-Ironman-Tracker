package com.dan.gimtracker.model;

public class RemoveGroupMemberRequest
{
	private final String inviteCode;
	private final String ownerPlayerName;
	private final String targetPlayerName;

	public RemoveGroupMemberRequest(String inviteCode, String ownerPlayerName, String targetPlayerName)
	{
		this.inviteCode = inviteCode;
		this.ownerPlayerName = ownerPlayerName;
		this.targetPlayerName = targetPlayerName;
	}

	public String getInviteCode()
	{
		return inviteCode;
	}

	public String getOwnerPlayerName()
	{
		return ownerPlayerName;
	}

	public String getTargetPlayerName()
	{
		return targetPlayerName;
	}
}
