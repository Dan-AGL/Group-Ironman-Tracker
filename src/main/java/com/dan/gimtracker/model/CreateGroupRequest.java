package com.dan.gimtracker.model;

public class CreateGroupRequest
{
	private final String groupName;
	private final String creatorPlayerName;

	public CreateGroupRequest(String groupName, String creatorPlayerName)
	{
		this.groupName = groupName;
		this.creatorPlayerName = creatorPlayerName;
	}

	public String getGroupName()
	{
		return groupName;
	}

	public String getCreatorPlayerName()
	{
		return creatorPlayerName;
	}
}
