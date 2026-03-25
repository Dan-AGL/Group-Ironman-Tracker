package com.dan.gimtracker;

import com.dan.gimtracker.model.ProgressUploadRequest;
import com.google.gson.Gson;
import java.io.IOException;
import java.time.Instant;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SyncService
{
	private static final MediaType JSON = MediaType.parse("application/json");

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
}
