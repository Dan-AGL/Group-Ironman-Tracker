package com.dan.gimtracker;

import com.dan.gimtracker.model.BackendEventResponse;
import com.dan.gimtracker.model.CreateGroupRequest;
import com.dan.gimtracker.model.GroupMemberResponse;
import com.dan.gimtracker.model.GroupResponse;
import com.dan.gimtracker.model.JoinGroupRequest;
import com.dan.gimtracker.model.LeaveGroupRequest;
import com.dan.gimtracker.model.ProgressUploadRequest;
import com.dan.gimtracker.model.RemoveGroupMemberRequest;
import com.dan.gimtracker.model.TrackedEvent;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
	name = "Group Ironman Tracker"
)
public class GIMTrackerPlugin extends Plugin
{
	private static final Logger log = LoggerFactory.getLogger(GIMTrackerPlugin.class);
	private static final String API_BASE_URL = "http://Group-Ironman-Tracker-env.eba-rmummppd.ap-southeast-2.elasticbeanstalk.com";
	private static final int MAX_DISPLAY_EVENTS = 20;
	private static final Duration SYNC_INTERVAL = Duration.ofSeconds(15);
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
	private static final Pattern COLLECTION_LOG_MESSAGE = Pattern.compile(
		"(.+?) received a new collection log item: (.+?) \\(([\\d,]+)/([\\d,]+)\\)\\.?$",
		Pattern.CASE_INSENSITIVE
	);

	@Inject
	private Client client;

	@Inject
	private GIMTrackerConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ClientToolbar clientToolbar;

	private final EventTracker eventTracker = new EventTracker();
	private final SyncService syncService = new SyncService();
	private final ExecutorService syncExecutor = Executors.newSingleThreadExecutor();
	private final Gson gson = new Gson();

	private ProgressPanel progressPanel;
	private NavigationButton navigationButton;
	private Instant lastSyncAttempt = Instant.EPOCH;
	private List<TrackedEvent> persistedRecentEvents = List.of();
	private volatile Set<String> currentGroupMembers = Set.of();

	@Override
	protected void startUp() throws Exception
	{
		log.debug("Group Ironman Tracker started");
		progressPanel = new ProgressPanel();
		progressPanel.setCreateGroupAction(this::createGroup);
		progressPanel.setLeaveGroupAction(this::leaveGroup);
		progressPanel.setJoinGroupAction(this::joinGroup);
		progressPanel.setShowMembersAction(this::showGroupMembers);
		progressPanel.setRemoveMemberAction(this::removeMember);
		navigationButton = NavigationButton.builder()
			.tooltip("Group Ironman Tracker")
			.icon(createToolbarIcon())
			.priority(5)
			.panel(progressPanel)
			.build();
		clientToolbar.addNavigation(navigationButton);
		refreshPanel();
		refreshGroupDetails();

		if (client.getGameState() == GameState.LOGGED_IN)
		{
			eventTracker.resetLevelBaseline();
			eventTracker.resetBossCountBaseline();
			eventTracker.resetCombatTaskBaseline();
			eventTracker.resetCollectionLogBaseline();
		}
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.debug("Group Ironman Tracker stopped");
		flushPendingEvents("shutdown");
		clientToolbar.removeNavigation(navigationButton);
		syncExecutor.shutdownNow();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() == GameState.LOGGED_IN)
		{
			eventTracker.resetLevelBaseline();
			eventTracker.resetBossCountBaseline();
			eventTracker.resetCombatTaskBaseline();
			eventTracker.resetCollectionLogBaseline();
			progressPanel.updateStatus("Tracking level-ups");
			refreshPanel();
			refreshGroupDetails();
		}
		else if (gameStateChanged.getGameState() == GameState.LOGIN_SCREEN)
		{
			eventTracker.resetLevelBaseline();
			eventTracker.resetBossCountBaseline();
			eventTracker.resetCombatTaskBaseline();
			eventTracker.resetCollectionLogBaseline();
			flushPendingEvents("logout");
			progressPanel.updateStatus("Waiting for login");
			refreshPanel();
		}
	}

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

		tryCaptureCollectionLogEvent(message, event.getType());
	}

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
		Player localPlayer = client.getLocalPlayer();
		String playerName = localPlayer == null ? "Unknown" : localPlayer.getName();
		List<TrackedEvent> newEvents = eventTracker.captureBossKillCountEvent(
			playerName,
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
		Player localPlayer = client.getLocalPlayer();
		String playerName = localPlayer == null ? "Unknown" : localPlayer.getName();
		List<TrackedEvent> newEvents = eventTracker.captureBossDropEvent(
			bossName,
			itemName,
			value,
			playerName,
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

	private boolean tryCaptureCombatTaskEvent(String message, ChatMessageType messageType)
	{
		String taskName;
		String tier;
		Player localPlayer = client.getLocalPlayer();
		String playerName = localPlayer == null ? "Unknown" : localPlayer.getName();

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
			playerName,
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

	private boolean tryCaptureCollectionLogEvent(String message, ChatMessageType messageType)
	{
		Matcher matcher = COLLECTION_LOG_MESSAGE.matcher(message);
		if (!matcher.matches())
		{
			return false;
		}

		String playerName = matcher.group(1);
		if (!isCurrentGroupMember(playerName))
		{
			return false;
		}

		String itemName = matcher.group(2);
		int unlockedCount = Integer.parseInt(matcher.group(3).replace(",", ""));
		int totalCount = Integer.parseInt(matcher.group(4).replace(",", ""));
		List<TrackedEvent> newEvents = eventTracker.captureCollectionLogEvent(
			playerName,
			itemName,
			unlockedCount,
			totalCount,
			messageType.name()
		);

		if (!newEvents.isEmpty())
		{
			log.debug("Captured {} collection log events", newEvents.size());
			progressPanel.updateStatus("Captured collection log unlock");
			refreshPanel();
		}

		return !newEvents.isEmpty();
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (client.getGameState() != GameState.LOGGED_IN || eventTracker.getPendingCount() == 0)
		{
			return;
		}

		if (Duration.between(lastSyncAttempt, Instant.now()).compareTo(SYNC_INTERVAL) >= 0)
		{
			flushPendingEvents("timer");
		}
	}

	@Provides
	GIMTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GIMTrackerConfig.class);
	}

	private void flushPendingEvents(String reason)
	{
		if (config.groupCode().isBlank())
		{
			progressPanel.updateStatus("Create or join a group");
			refreshPanel();
			return;
		}

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
				boolean success = syncService.sendEvents(API_BASE_URL, request);
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
					refreshPersistedEvents();
				}
			}
			catch (IOException | IllegalArgumentException ex)
			{
				eventTracker.requeue(events);
				log.warn("Sync failed during {}", reason, ex);
				progressPanel.updateStatus("Sync failed");
			}

			refreshPanel();
		});
	}

	private void refreshPanel()
	{
		if (progressPanel == null)
		{
			return;
		}

		progressPanel.updatePendingCount(eventTracker.getPendingCount());
		progressPanel.updateLastSync(syncService.getLastSuccessfulSync());
		progressPanel.updateRecentEvents(buildDisplayEvents());
		progressPanel.updateGroup(config.groupName(), config.groupCode());
	}

	private void createGroup()
	{
		Player localPlayer = client.getLocalPlayer();
		String playerName = localPlayer == null ? "" : localPlayer.getName();
		String groupName = progressPanel.promptForValue("Create Group", "Enter a group name");
		if (groupName == null || groupName.trim().isEmpty() || playerName.isBlank())
		{
			return;
		}

		progressPanel.updateStatus("Creating group...");
		syncExecutor.submit(() ->
		{
			try
			{
				GroupResponse group = syncService.createGroup(
					API_BASE_URL,
					new CreateGroupRequest(groupName.trim(), playerName)
				);
				saveGroup(group);
				progressPanel.updateStatus("Created group " + group.getName());
				refreshGroupDetails();
			}
			catch (IOException | IllegalArgumentException ex)
			{
				log.warn("Failed to create group", ex);
				progressPanel.updateStatus("Group create failed");
			}
		});
	}

	private void joinGroup()
	{
		Player localPlayer = client.getLocalPlayer();
		String playerName = localPlayer == null ? "" : localPlayer.getName();
		String inviteCode = progressPanel.promptForSensitiveValue("Join Group", "Enter the invite code");
		if (inviteCode == null || inviteCode.trim().isEmpty() || playerName.isBlank())
		{
			return;
		}

		progressPanel.updateStatus("Joining group...");
		syncExecutor.submit(() ->
		{
			try
			{
				GroupResponse group = syncService.joinGroup(
					API_BASE_URL,
					new JoinGroupRequest(inviteCode.trim(), playerName)
				);
				saveGroup(group);
				progressPanel.updateStatus("Joined " + group.getName());
				refreshGroupDetails();
			}
			catch (IOException | IllegalArgumentException ex)
			{
				log.warn("Failed to join group", ex);
				progressPanel.updateStatus("Group join failed");
			}
		});
	}

	private void refreshGroupDetails()
	{
		String inviteCode = config.groupCode();
		if (inviteCode.isBlank())
		{
			progressPanel.updateGroup(config.groupName(), "");
			progressPanel.updateMembers(List.of(), false, "");
			persistedRecentEvents = List.of();
			currentGroupMembers = Set.of();
			refreshPanel();
			return;
		}

		syncExecutor.submit(() ->
		{
			try
			{
				GroupResponse group = syncService.fetchGroup(API_BASE_URL, inviteCode);
				List<GroupMemberResponse> members = syncService.fetchMembers(API_BASE_URL, inviteCode);
				saveGroup(group);
				Player localPlayer = client.getLocalPlayer();
				String localPlayerName = localPlayer == null ? "" : localPlayer.getName();
				boolean canRemoveMembers = members.stream().anyMatch(member ->
					"OWNER".equalsIgnoreCase(member.getRole()) && member.getPlayerName().equalsIgnoreCase(localPlayerName)
				);
				currentGroupMembers = members.stream()
					.map(GroupMemberResponse::getPlayerName)
					.filter(name -> name != null && !name.isBlank())
					.map(this::normalizePlayerName)
					.collect(Collectors.toCollection(HashSet::new));
				progressPanel.updateMembers(
					members.stream()
						.map(member -> new ProgressPanel.GroupMemberView(member.getPlayerName(), member.getRole()))
						.collect(Collectors.toList()),
					canRemoveMembers,
					localPlayerName
				);
				refreshPersistedEvents();
			}
			catch (IOException | IllegalArgumentException ex)
			{
				log.warn("Failed to refresh group details", ex);
				progressPanel.updateStatus("Group refresh failed");
			}
		});
	}

	private void showGroupMembers()
	{
		String inviteCode = config.groupCode();
		if (inviteCode.isBlank())
		{
			progressPanel.showMembersDialog();
			return;
		}

		syncExecutor.submit(() ->
		{
			try
			{
				List<GroupMemberResponse> members = syncService.fetchMembers(API_BASE_URL, inviteCode);
				Player localPlayer = client.getLocalPlayer();
				String localPlayerName = localPlayer == null ? "" : localPlayer.getName();
				boolean canRemoveMembers = members.stream().anyMatch(member ->
					"OWNER".equalsIgnoreCase(member.getRole()) && member.getPlayerName().equalsIgnoreCase(localPlayerName)
				);
				currentGroupMembers = members.stream()
					.map(GroupMemberResponse::getPlayerName)
					.filter(name -> name != null && !name.isBlank())
					.map(this::normalizePlayerName)
					.collect(Collectors.toCollection(HashSet::new));
				progressPanel.updateMembers(
					members.stream()
						.map(member -> new ProgressPanel.GroupMemberView(member.getPlayerName(), member.getRole()))
						.collect(Collectors.toList()),
					canRemoveMembers,
					localPlayerName
				);
				progressPanel.showMembersDialog();
			}
			catch (IOException | IllegalArgumentException ex)
			{
				log.warn("Failed to fetch group members", ex);
				progressPanel.updateStatus("Group members refresh failed");
			}
		});
	}

	private void leaveGroup()
	{
		Player localPlayer = client.getLocalPlayer();
		String playerName = localPlayer == null ? "" : localPlayer.getName();
		if (config.groupCode().isBlank() || playerName.isBlank())
		{
			return;
		}

		boolean confirmed = progressPanel.confirm(
			"Leave Group",
			"Are you sure you want to leave " + config.groupName() + "?"
		);
		if (!confirmed)
		{
			return;
		}

		progressPanel.updateStatus("Leaving group...");
		syncExecutor.submit(() ->
		{
			try
			{
				syncService.leaveGroup(
					API_BASE_URL,
					new LeaveGroupRequest(config.groupCode(), playerName)
				);
				clearSavedGroup();
				progressPanel.updateMembers(List.of(), false, "");
				persistedRecentEvents = List.of();
				currentGroupMembers = Set.of();
				progressPanel.updateStatus("Left group");
				refreshPanel();
			}
			catch (IOException | IllegalArgumentException ex)
			{
				log.warn("Failed to leave group", ex);
				progressPanel.updateStatus("Leave group failed");
			}
		});
	}

	private void removeMember(String memberName)
	{
		Player localPlayer = client.getLocalPlayer();
		String ownerPlayerName = localPlayer == null ? "" : localPlayer.getName();
		if (config.groupCode().isBlank() || ownerPlayerName.isBlank() || memberName == null || memberName.isBlank())
		{
			return;
		}

		progressPanel.updateStatus("Removing member...");
		syncExecutor.submit(() ->
		{
			try
			{
				syncService.removeGroupMember(
					API_BASE_URL,
					new RemoveGroupMemberRequest(config.groupCode(), ownerPlayerName, memberName)
				);
				progressPanel.updateStatus("Removed " + memberName);
				refreshGroupDetails();
			}
			catch (IOException | IllegalArgumentException ex)
			{
				log.warn("Failed to remove member {}", memberName, ex);
				progressPanel.updateStatus("Remove member failed");
			}
		});
	}

	private void saveGroup(GroupResponse group)
	{
		configManager.setConfiguration("gimtracker", "groupCode", group.getInviteCode());
		configManager.setConfiguration("gimtracker", "groupName", group.getName());
	}

	private void clearSavedGroup()
	{
		configManager.setConfiguration("gimtracker", "groupCode", "");
		configManager.setConfiguration("gimtracker", "groupName", "");
	}

	private void refreshPersistedEvents()
	{
		String inviteCode = config.groupCode();
		if (inviteCode.isBlank())
		{
			persistedRecentEvents = List.of();
			refreshPanel();
			return;
		}

		try
		{
			List<BackendEventResponse> backendEvents = syncService.fetchGroupEvents(API_BASE_URL, inviteCode);
			persistedRecentEvents = collapsePersistedEvents(backendEvents.stream()
				.map(this::toTrackedEvent)
				.filter(event -> event != null)
				.collect(Collectors.toList()));
			refreshPanel();
		}
		catch (IOException | IllegalArgumentException ex)
		{
			log.warn("Failed to refresh persisted events", ex);
			progressPanel.updateStatus("History refresh failed");
		}
	}

	private List<TrackedEvent> buildDisplayEvents()
	{
		LinkedHashMap<String, TrackedEvent> combined = new LinkedHashMap<>();
		List<TrackedEvent> localEvents = eventTracker.getRecentEvents();
		for (TrackedEvent event : localEvents)
		{
			combined.put(eventKey(event), event);
		}

		for (int index = persistedRecentEvents.size() - 1; index >= 0; index--)
		{
			TrackedEvent event = persistedRecentEvents.get(index);
			combined.putIfAbsent(eventKey(event), event);
		}

		return combined.values()
			.stream()
			.limit(MAX_DISPLAY_EVENTS)
			.collect(Collectors.toList());
	}

	private boolean isCurrentGroupMember(String playerName)
	{
		String normalizedPlayerName = normalizePlayerName(playerName);
		if (normalizedPlayerName.isEmpty())
		{
			return false;
		}

		Set<String> members = currentGroupMembers;
		if (members.isEmpty())
		{
			Player localPlayer = client.getLocalPlayer();
			return localPlayer != null && normalizedPlayerName.equals(normalizePlayerName(localPlayer.getName()));
		}

		return members.contains(normalizedPlayerName);
	}

	private String normalizePlayerName(String playerName)
	{
		if (playerName == null)
		{
			return "";
		}

		return playerName.trim().toLowerCase(Locale.ENGLISH);
	}

	private String eventKey(TrackedEvent event)
	{
		return event.getType() + "|" + event.getTimestamp() + "|" + event.getSummary();
	}

	private TrackedEvent toTrackedEvent(BackendEventResponse backendEvent)
	{
		Map<String, Object> payload = gson.fromJson(
			backendEvent.getPayloadJson(),
			new TypeToken<Map<String, Object>>() { }.getType()
		);
		if (payload == null)
		{
			return null;
		}

		Object summary = payload.get("summary");
		Object details = payload.get("details");
		if (!(details instanceof Map))
		{
			return null;
		}

		Map<String, Object> typedDetails = new LinkedHashMap<>((Map<String, Object>) details);
		return new TrackedEvent(
			backendEvent.getEventType(),
			backendEvent.getEventTime(),
			summary == null ? backendEvent.getEventType() : String.valueOf(summary),
			typedDetails
		);
	}

	private List<TrackedEvent> collapsePersistedEvents(List<TrackedEvent> events)
	{
		List<TrackedEvent> collapsed = new ArrayList<>();
		Map<String, Integer> activeBossKcIndexes = new LinkedHashMap<>();
		for (TrackedEvent event : events)
		{
			if (!"BOSS_KC".equals(event.getType()))
			{
				collapsed.add(event);
				continue;
			}

			String streamKey = bossKcStreamKey(event);
			Integer existingIndex = activeBossKcIndexes.get(streamKey);
			if (existingIndex == null)
			{
				activeBossKcIndexes.put(streamKey, collapsed.size());
				collapsed.add(event);
				continue;
			}

			TrackedEvent previous = collapsed.get(existingIndex);
			if (isSameBossKcSession(previous, event))
			{
				collapsed.remove((int) existingIndex);
				collapsed.add(event);
				reindexBossKcStreams(activeBossKcIndexes, existingIndex);
				activeBossKcIndexes.put(streamKey, collapsed.size() - 1);
			}
			else
			{
				activeBossKcIndexes.put(streamKey, collapsed.size());
				collapsed.add(event);
			}
		}

		Collections.reverse(collapsed);
		return collapsed;
	}

	private void reindexBossKcStreams(Map<String, Integer> activeBossKcIndexes, int removedIndex)
	{
		for (Map.Entry<String, Integer> entry : activeBossKcIndexes.entrySet())
		{
			if (entry.getValue() > removedIndex)
			{
				entry.setValue(entry.getValue() - 1);
			}
		}
	}

	private String bossKcStreamKey(TrackedEvent event)
	{
		Map<String, Object> details = event.getDetails();
		return String.valueOf(details.get("playerName")) + "|"
			+ String.valueOf(details.get("bossName")) + "|"
			+ String.valueOf(details.get("countType"));
	}

	private boolean isSameBossKcSession(TrackedEvent previous, TrackedEvent current)
	{
		if (!"BOSS_KC".equals(previous.getType()) || !"BOSS_KC".equals(current.getType()))
		{
			return false;
		}

		Map<String, Object> previousDetails = previous.getDetails();
		Map<String, Object> currentDetails = current.getDetails();
		if (!String.valueOf(previousDetails.get("playerName")).equals(String.valueOf(currentDetails.get("playerName"))))
		{
			return false;
		}
		if (!String.valueOf(previousDetails.get("bossName")).equals(String.valueOf(currentDetails.get("bossName"))))
		{
			return false;
		}
		if (!String.valueOf(previousDetails.get("countType")).equals(String.valueOf(currentDetails.get("countType"))))
		{
			return false;
		}

		int previousSessionCount = readInt(previousDetails.get("count"));
		int currentSessionCount = readInt(currentDetails.get("count"));
		int previousTotalCount = readInt(previousDetails.get("totalCount"));
		int currentTotalCount = readInt(currentDetails.get("totalCount"));

		return currentSessionCount > previousSessionCount && currentTotalCount > previousTotalCount;
	}

	private int readInt(Object value)
	{
		if (value instanceof Number)
		{
			return ((Number) value).intValue();
		}

		if (value == null)
		{
			return -1;
		}

		try
		{
			return Integer.parseInt(String.valueOf(value));
		}
		catch (NumberFormatException ex)
		{
			return -1;
		}
	}

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
