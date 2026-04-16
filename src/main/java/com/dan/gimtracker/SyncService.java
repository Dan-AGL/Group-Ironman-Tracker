package com.dan.gimtracker;

import com.dan.gimtracker.model.CreateGroupRequest;
import com.dan.gimtracker.model.BackendEventResponse;
import com.dan.gimtracker.model.GroupMemberResponse;
import com.dan.gimtracker.model.GroupResponse;
import com.dan.gimtracker.model.JoinGroupRequest;
import com.dan.gimtracker.model.LeaveGroupRequest;
import com.dan.gimtracker.model.ProgressUploadRequest;
import com.dan.gimtracker.model.RemoveGroupMemberRequest;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.List;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SyncService
{
	private static final MediaType JSON = MediaType.parse("application/json");
	private static final Type BACKEND_EVENT_LIST_TYPE = new TypeToken<List<BackendEventResponse>>() { }.getType();
	private static final Type GROUP_MEMBER_LIST_TYPE = new TypeToken<List<GroupMemberResponse>>() { }.getType();

	private final OkHttpClient httpClient = new OkHttpClient();
	private final Gson gson = new Gson();
	private Instant lastSuccessfulSync;

	// Sends the queued event payload to the backend and records the time of the latest successful upload.
	public boolean sendEvents(String apiBaseUrl, ProgressUploadRequest request) throws IOException
	{
		RequestBody body = RequestBody.create(JSON, gson.toJson(request));
		Request httpRequest = new Request.Builder()
			.url(normalizeBaseUrl(apiBaseUrl) + "/api/progress")
			.post(body)
			.build();

		try (Response response = httpClient.newCall(httpRequest).execute())
		{
			if (!response.isSuccessful())
			{
				return false;
			}

			lastSuccessfulSync = Instant.now();
			return true;
		}
	}

	public GroupResponse createGroup(String apiBaseUrl, CreateGroupRequest request) throws IOException
	{
		return postJson(normalizeBaseUrl(apiBaseUrl) + "/api/groups", request, GroupResponse.class);
	}

	public GroupResponse joinGroup(String apiBaseUrl, JoinGroupRequest request) throws IOException
	{
		return postJson(normalizeBaseUrl(apiBaseUrl) + "/api/groups/join", request, GroupResponse.class);
	}

	public void leaveGroup(String apiBaseUrl, LeaveGroupRequest request) throws IOException
	{
		RequestBody body = RequestBody.create(JSON, gson.toJson(request));
		Request httpRequest = new Request.Builder()
			.url(normalizeBaseUrl(apiBaseUrl) + "/api/groups/leave")
			.post(body)
			.build();
		executeNoContent(httpRequest);
	}

	public void removeGroupMember(String apiBaseUrl, RemoveGroupMemberRequest request) throws IOException
	{
		RequestBody body = RequestBody.create(JSON, gson.toJson(request));
		Request httpRequest = new Request.Builder()
			.url(normalizeBaseUrl(apiBaseUrl) + "/api/groups/remove-member")
			.post(body)
			.build();
		executeNoContent(httpRequest);
	}

	public GroupResponse fetchGroup(String apiBaseUrl, String inviteCode) throws IOException
	{
		Request httpRequest = new Request.Builder()
			.url(normalizeBaseUrl(apiBaseUrl) + "/api/groups/" + inviteCode)
			.get()
			.build();
		return executeJson(httpRequest, GroupResponse.class);
	}

	public List<GroupMemberResponse> fetchMembers(String apiBaseUrl, String inviteCode) throws IOException
	{
		Request httpRequest = new Request.Builder()
			.url(normalizeBaseUrl(apiBaseUrl) + "/api/groups/" + inviteCode + "/members")
			.get()
			.build();
		return executeJson(httpRequest, GROUP_MEMBER_LIST_TYPE);
	}

	public List<BackendEventResponse> fetchGroupEvents(String apiBaseUrl, String inviteCode) throws IOException
	{
		Request httpRequest = new Request.Builder()
			.url(normalizeBaseUrl(apiBaseUrl) + "/api/events/group/" + inviteCode)
			.get()
			.build();
		return executeJson(httpRequest, BACKEND_EVENT_LIST_TYPE);
	}

	// Lets the panel display the last known successful sync time.
	public Instant getLastSuccessfulSync()
	{
		return lastSuccessfulSync;
	}

	// Removes a trailing slash so endpoint construction stays consistent.
	private String normalizeBaseUrl(String apiBaseUrl)
	{
		if (apiBaseUrl.endsWith("/"))
		{
			return apiBaseUrl.substring(0, apiBaseUrl.length() - 1);
		}

		return apiBaseUrl;
	}

	private <T> T postJson(String url, Object requestBody, Class<T> responseType) throws IOException
	{
		RequestBody body = RequestBody.create(JSON, gson.toJson(requestBody));
		Request httpRequest = new Request.Builder()
			.url(url)
			.post(body)
			.build();
		return executeJson(httpRequest, responseType);
	}

	private <T> T executeJson(Request request, Class<T> responseType) throws IOException
	{
		try (Response response = httpClient.newCall(request).execute())
		{
			if (!response.isSuccessful())
			{
				throw new IOException("HTTP " + response.code());
			}

			if (response.body() == null)
			{
				throw new IOException("Empty response body");
			}

			return gson.fromJson(response.body().charStream(), responseType);
		}
	}

	private <T> T executeJson(Request request, Type responseType) throws IOException
	{
		try (Response response = httpClient.newCall(request).execute())
		{
			if (!response.isSuccessful())
			{
				throw new IOException("HTTP " + response.code());
			}

			if (response.body() == null)
			{
				throw new IOException("Empty response body");
			}

			return gson.fromJson(response.body().charStream(), responseType);
		}
	}

	private void executeNoContent(Request request) throws IOException
	{
		try (Response response = httpClient.newCall(request).execute())
		{
			if (!response.isSuccessful())
			{
				throw new IOException("HTTP " + response.code());
			}
		}
	}
}
