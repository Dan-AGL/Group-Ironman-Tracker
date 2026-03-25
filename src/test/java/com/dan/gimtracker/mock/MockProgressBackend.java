package com.dan.gimtracker.mock;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public class MockProgressBackend
{
	private static final int PORT = 8080;

	// Starts a tiny local HTTP server for manual Phase 2 testing without a real backend.
	public static void main(String[] args) throws IOException
	{
		HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
		server.createContext("/health", new HealthHandler());
		server.createContext("/api/progress", new ProgressHandler());
		server.setExecutor(null);
		server.start();

		System.out.println("Mock backend listening on http://localhost:" + PORT);
		System.out.println("Health check: http://localhost:" + PORT + "/health");
		System.out.println("Progress endpoint: POST http://localhost:" + PORT + "/api/progress");
	}

	private static class HealthHandler implements HttpHandler
	{
		// Returns a simple health response so the browser can confirm the mock server is up.
		@Override
		public void handle(HttpExchange exchange) throws IOException
		{
			if (!"GET".equalsIgnoreCase(exchange.getRequestMethod()))
			{
				sendResponse(exchange, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}");
				return;
			}

			sendResponse(exchange, 200, "{\"ok\":true,\"status\":\"healthy\"}");
		}
	}

	private static class ProgressHandler implements HttpHandler
	{
		// Accepts the plugin's POST payload and prints it to the terminal for inspection.
		@Override
		public void handle(HttpExchange exchange) throws IOException
		{
			if (!"POST".equalsIgnoreCase(exchange.getRequestMethod()))
			{
				sendResponse(exchange, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}");
				return;
			}

			String body = readBody(exchange.getRequestBody());
			System.out.println();
			System.out.println("[" + Instant.now() + "] Received progress payload:");
			System.out.println(body);

			sendResponse(exchange, 200, "{\"ok\":true}");
		}
	}

	// Reads the raw request body so the mock server can print the exact payload it received.
	private static String readBody(InputStream inputStream) throws IOException
	{
		return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
	}

	// Sends a small JSON response back to the caller.
	private static void sendResponse(HttpExchange exchange, int statusCode, String body) throws IOException
	{
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
		exchange.sendResponseHeaders(statusCode, bytes.length);
		try (OutputStream outputStream = exchange.getResponseBody())
		{
			outputStream.write(bytes);
		}
	}
}
