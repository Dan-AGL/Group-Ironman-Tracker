package com.dan.gimtracker.model;

public class GroupResponse
{
	private long id;
	private String name;
	private String inviteCode;
	private String createdBy;

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
}
