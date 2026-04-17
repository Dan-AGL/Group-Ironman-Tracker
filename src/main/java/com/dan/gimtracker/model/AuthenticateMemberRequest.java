package com.dan.gimtracker.model;

public class AuthenticateMemberRequest
{
	private final String inviteCode;
	private final String authCode;

	public AuthenticateMemberRequest(String inviteCode, String authCode)
	{
		this.inviteCode = inviteCode;
		this.authCode = authCode;
	}

	public String getInviteCode()
	{
		return inviteCode;
	}

	public String getAuthCode()
	{
		return authCode;
	}
}
