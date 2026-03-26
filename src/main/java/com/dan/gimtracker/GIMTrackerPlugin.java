package com.dan.gimtracker;

import com.dan.gimtracker.model.ProgressUploadRequest;
import com.dan.gimtracker.model.TrackedEvent;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Group Ironman Tracker"
)
public class GIMTrackerPlugin extends Plugin
{
	private static final Duration MIN_SYNC_INTERVAL = Duration.ofSeconds(5);
	private static final Pattern BOSS_COUNT_MESSAGE = Pattern.compile(
		"Your (.+?) (kill count|completion count) is:? ([\\d,]+)\\.?$"
	);
	private static final Pattern BOSS_DROP_MESSAGE = Pattern.compile(
		"(?:.+? received a drop: |Drop: )(.+?) \\(([\\d,]+) coins\\) from (.+?)\\.?$"
	);
	private static final Pattern COMBAT_TASK_MESSAGE = Pattern.compile(
		"(?:Congratulations,? )?(?:you've|you have) completed (?:a |the )?(?:(easy|medium|hard|elite|master|grandmaster) combat task|combat achievement task):? (.+?)(?: \\((\\d+) points?\\))?\\.?$",
		Pattern.CASE_INSENSITIVE
	);
	private static final Pattern COMBAT_TASK_QUOTED_MESSAGE = Pattern.compile(
		"Congratulations!? (?:you've|you have) completed (?:an? )?(easy|medium|hard|elite|master|grandmaster) combat achievement '?(.+?)'?(?: \\((\\d+) points?\\))?\\.?$",
		Pattern.CASE_INSENSITIVE
	);

	@Inject
	private Client client;

	@Inject
	private GIMTrackerConfig config;

	@Inject
	private ClientToolbar clientToolbar;

	private final EventTracker eventTracker = new EventTracker();
	private final SyncService syncService = new SyncService();
	private final ExecutorService syncExecutor = Executors.newSingleThreadExecutor();

	private ProgressPanel progressPanel;
	private NavigationButton navigationButton;
	private Instant lastSyncAttempt = Instant.EPOCH;

	// Creates the sidebar entry point, initializes the panel state, and seeds tracking if already logged in.
	@Override
	protected void startUp() throws Exception
	{
		log.debug("Group Ironman Tracker started");
		progressPanel = new ProgressPanel(
			this::requestManualSync,
			this::addTestLevelUpEvent,
			this::addTestCombatTaskEvent,
			config.developerMode()
		);
		navigationButton = NavigationButton.builder()
			.tooltip("Group Ironman Tracker")
			.icon(createToolbarIcon())
			.priority(5)
			.panel(progressPanel)
			.build();
		clientToolbar.addNavigation(navigationButton);
		refreshPanel();

		if (client.getGameState() == GameState.LOGGED_IN)
		{
			eventTracker.resetLevelBaseline();
			eventTracker.resetBossCountBaseline();
			eventTracker.resetCombatTaskBaseline();
		}
	}

	// Flushes pending work on shutdown and removes the sidebar integration cleanly.
	@Override
	protected void shutDown() throws Exception
	{
		log.debug("Group Ironman Tracker stopped");
		flushPendingEvents("shutdown");
		clientToolbar.removeNavigation(navigationButton);
		syncExecutor.shutdownNow();
	}

	// Resets tracking on login and attempts a final sync when returning to the login screen.
	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() == GameState.LOGGED_IN)
		{
			eventTracker.resetLevelBaseline();
			eventTracker.resetBossCountBaseline();
			eventTracker.resetCombatTaskBaseline();
			progressPanel.updateStatus("Tracking level-ups");
			refreshPanel();
		}
		else if (gameStateChanged.getGameState() == GameState.LOGIN_SCREEN)
		{
			eventTracker.resetLevelBaseline();
			eventTracker.resetBossCountBaseline();
			eventTracker.resetCombatTaskBaseline();
			flushPendingEvents("logout");
			progressPanel.updateStatus("Waiting for login");
			refreshPanel();
		}
	}

	// Converts stat changes into queued level-up events when the player's real level increases.
	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		List<TrackedEvent> newEvents = eventTracker.captureLevelUpEvents(client, event);
		if (!newEvents.isEmpty())
		{
			log.debug("Captured {} new tracked events", newEvents.size());
			refreshPanel();
		}
	}

	// Parses boss KC and raid completion chat messages into queued events once they clear the configured threshold.
	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		String message = Text.removeTags(event.getMessage()).trim();
		if (tryCaptureBossCountEvent(message))
		{
			return;
		}

		if (tryCaptureBossDropEvent(message, event.getType()))
		{
			return;
		}

		if (tryCaptureCombatTaskEvent(message, event.getType()))
		{
			return;
		}

		if (config.developerMode() && message.toLowerCase().contains("drop"))
		{
			log.debug("Unmatched drop chat [{}]: {}", event.getType(), message);
		}

		if (config.developerMode() && message.toLowerCase().contains("combat achievement"))
		{
			log.debug("Unmatched combat task chat [{}]: {}", event.getType(), message);
		}
	}

	// Extracts boss KC and completion events from game chat messages.
	private boolean tryCaptureBossCountEvent(String message)
	{
		Matcher matcher = BOSS_COUNT_MESSAGE.matcher(message);
		if (!matcher.matches())
		{
			return false;
		}

		String bossName = matcher.group(1);
		String countType = matcher.group(2).equals("completion count") ? "COMPLETION_COUNT" : "KILL_COUNT";
		int count = Integer.parseInt(matcher.group(3).replace(",", ""));
		List<TrackedEvent> newEvents = eventTracker.captureBossKillCountEvent(
			bossName,
			countType,
			count,
			config.bossKillCountThreshold()
		);

		if (!newEvents.isEmpty())
		{
			log.debug("Captured {} boss KC events", newEvents.size());
			progressPanel.updateStatus("Captured boss KC for " + bossName);
			refreshPanel();
		}

		return !newEvents.isEmpty();
	}

	// Extracts boss drop events from game, clan, or group-style chat messages when the value is high enough.
	private boolean tryCaptureBossDropEvent(String message, ChatMessageType messageType)
	{
		Matcher matcher = BOSS_DROP_MESSAGE.matcher(message);
		if (!matcher.matches())
		{
			return false;
		}

		String itemName = matcher.group(1);
		long value = Long.parseLong(matcher.group(2).replace(",", ""));
		String bossName = matcher.group(3);
		List<TrackedEvent> newEvents = eventTracker.captureBossDropEvent(
			bossName,
			itemName,
			value,
			messageType.name(),
			config.dropValueThreshold()
		);

		if (!newEvents.isEmpty())
		{
			log.debug("Captured {} boss drop events", newEvents.size());
			progressPanel.updateStatus("Captured drop from " + bossName);
			refreshPanel();
		}

		return !newEvents.isEmpty();
	}

	// Extracts combat task completions from chat messages and de-duplicates them within the session.
	private boolean tryCaptureCombatTaskEvent(String message, ChatMessageType messageType)
	{
		String taskName;
		String tier;

		Matcher directMatcher = COMBAT_TASK_MESSAGE.matcher(message);
		if (directMatcher.matches())
		{
			tier = directMatcher.group(1) == null ? "" : directMatcher.group(1).toUpperCase();
			taskName = directMatcher.group(2);
		}
		else
		{
			Matcher quotedMatcher = COMBAT_TASK_QUOTED_MESSAGE.matcher(message);
			if (!quotedMatcher.matches())
			{
				return false;
			}

			tier = quotedMatcher.group(1).toUpperCase();
			taskName = quotedMatcher.group(2);
		}

		List<TrackedEvent> newEvents = eventTracker.captureCombatTaskEvent(
			taskName,
			tier,
			messageType.name()
		);

		if (!newEvents.isEmpty())
		{
			log.debug("Captured {} combat task events", newEvents.size());
			progressPanel.updateStatus("Captured combat task completion");
			refreshPanel();
		}

		return !newEvents.isEmpty();
	}

	// Periodically flushes queued events instead of sending one request per RuneLite event.
	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (client.getGameState() != GameState.LOGGED_IN || eventTracker.getPendingCount() == 0)
		{
			return;
		}

		Duration syncInterval = Duration.ofSeconds(Math.max(config.syncIntervalSeconds(), MIN_SYNC_INTERVAL.getSeconds()));
		if (Duration.between(lastSyncAttempt, Instant.now()).compareTo(syncInterval) >= 0)
		{
			flushPendingEvents("timer");
		}
	}

	// Exposes the plugin config to RuneLite's config manager.
	@Provides
	GIMTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GIMTrackerConfig.class);
	}

	// Handles the panel button by routing it through the standard sync path.
	private void requestManualSync()
	{
		flushPendingEvents("manual");
	}

	// Injects a synthetic level-up event so the pipeline can be tested on high-level accounts.
	private void addTestLevelUpEvent()
	{
		eventTracker.addTestEvent(TrackedEvent.testLevelUp());
		progressPanel.updateStatus("Queued test level-up");
		refreshPanel();
	}

	// Injects a synthetic combat task event so the pipeline can be tested without spending a real completion.
	private void addTestCombatTaskEvent()
	{
		eventTracker.addTestEvent(TrackedEvent.testCombatTask());
		progressPanel.updateStatus("Queued test combat task");
		refreshPanel();
	}

	// Moves queued events into an upload request and sends them off-thread so the client stays responsive.
	private void flushPendingEvents(String reason)
	{
		List<TrackedEvent> events = eventTracker.drainPendingEvents();
		if (events.isEmpty())
		{
			refreshPanel();
			return;
		}

		Player localPlayer = client.getLocalPlayer();
		String playerName = localPlayer == null ? "Unknown" : localPlayer.getName();
		ProgressUploadRequest request = new ProgressUploadRequest(
			config.groupCode(),
			playerName,
			Instant.now().toString(),
			events
		);

		lastSyncAttempt = Instant.now();
		progressPanel.updateStatus("Syncing " + events.size() + " events...");
		refreshPanel();

		syncExecutor.submit(() ->
		{
			try
			{
				boolean success = syncService.sendEvents(config.apiBaseUrl(), request);
				if (!success)
				{
					eventTracker.requeue(events);
					log.warn("Sync failed with non-success response during {}", reason);
					progressPanel.updateStatus("Sync failed");
				}
				else
				{
					log.debug("Synced {} events via {}", events.size(), reason);
					progressPanel.updateStatus("Last sync succeeded");
				}
			}
			catch (IOException ex)
			{
				eventTracker.requeue(events);
				log.warn("Sync failed during {}", reason, ex);
				progressPanel.updateStatus("Sync failed");
			}

			refreshPanel();
		});
	}

	// Pushes the latest tracker and sync state into the sidebar.
	private void refreshPanel()
	{
		if (progressPanel == null)
		{
			return;
		}

		progressPanel.setDeveloperMode(config.developerMode());
		progressPanel.updatePendingCount(eventTracker.getPendingCount());
		progressPanel.updateLastSync(syncService.getLastSuccessfulSync());
		progressPanel.updateRecentEvents(eventTracker.getRecentEvents());
	}

	// Generates a simple in-code toolbar icon so the plugin does not depend on external image assets yet.
	private BufferedImage createToolbarIcon()
	{
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setColor(new Color(34, 60, 80));
		graphics.fillRoundRect(0, 0, 16, 16, 4, 4);
		graphics.setColor(new Color(235, 235, 220));
		graphics.drawString("G", 4, 12);
		graphics.dispose();
		return image;
	}
}
