package com.dan.gimtracker;

import com.dan.gimtracker.model.AuthenticatedGroupResponse;
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
	private static final int GROUP_AUTH_CODE_LENGTH = 8;
	private static final int MAX_GROUP_NAME_LENGTH = 25;
	private static final int MAX_DISPLAY_EVENTS = 20;
	private static final Duration SYNC_INTERVAL = Duration.ofSeconds(15);
	private static final Pattern BOSS_COUNT_MESSAGE = Pattern.compile(
		"Your (.+?) (kill count|completion count) is:? ([\\d,]+)\\.?$"
	);
	private static final Pattern BOSS_DROP_MESSAGE = Pattern.compile(
		"(?:.+? received a drop: |Drop: )(.+?) \\(([\\d,]+) coins\\) from (.+?)\\.?$"
	);
	private static final Pattern NAMED_BOSS_DROP_MESSAGE = Pattern.compile(
		"(.+?) received a drop: (.+?) \\(([\\d,]+) coins\\) from (.+?)\\.?$"
	);
	private static final Pattern COMBAT_TASK_MESSAGE = Pattern.compile(
		"(?:Congratulations,? )?(?:you've|you have) completed (?:a |the )?(?:(easy|medium|hard|elite|master|grandmaster) combat task|combat achievement task):? (.+?)(?: \\((\\d+) points?\\))?\\.?$",
		Pattern.CASE_INSENSITIVE
	);
	private static final Pattern COMBAT_TASK_QUOTED_MESSAGE = Pattern.compile(
		"Congratulations!? (?:you've|you have) completed (?:an? )?(easy|medium|hard|elite|master|grandmaster) combat achievement '?(.+?)'?(?: \\((\\d+) points?\\))?\\.?$",
		Pattern.CASE_INSENSITIVE
	);
	private static final Pattern NAMED_COMBAT_TASK_MESSAGE = Pattern.compile(
		"(.+?) has completed (?:an? |the )?(?:(easy|medium|hard|elite|master|grandmaster) )?combat (?:task|achievement task):? (.+?)\\.?$",
		Pattern.CASE_INSENSITIVE
	);
	private static final Pattern COLLECTION_LOG_MESSAGE = Pattern.compile(
		"(.+?) received a new collection log item: (.+?) \\(([\\d,]+)/([\\d,]+)\\)\\.?$",
		Pattern.CASE_INSENSITIVE
	);
	private static final Pattern QUEST_COMPLETION_MESSAGE = Pattern.compile(
		"^(?:Congratulations,? )?(?:you've|you have) completed a quest: (.+?)\\.?$",
		Pattern.CASE_INSENSITIVE
	);
	private static final Pattern ACHIEVEMENT_DIARY_MESSAGE = Pattern.compile(
		"^(.+?) has completed the (easy|medium|hard|elite) (.+?) diary\\.?$",
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

	@Inject
	private Gson gson;

	@Inject
	private SyncService syncService;

	private final EventTracker eventTracker = new EventTracker();
	private final ExecutorService syncExecutor = Executors.newSingleThreadExecutor();

	private ProgressPanel progressPanel;
	private NavigationButton navigationButton;
	private Instant lastSyncAttempt = Instant.EPOCH;
	private Instant lastRefreshAttempt = Instant.EPOCH;
	private Instant currentLoginSessionStart = Instant.EPOCH;
	private List<TrackedEvent> persistedRecentEvents = List.of();
	private volatile Set<String> currentGroupMembers = Set.of();
	private volatile String currentGroupCode = "";
	private volatile String currentGroupName = "";
	private volatile String lastKnownPlayerName = "";
	private boolean loginSessionResetPending = false;

	@Override
	protected void startUp() throws Exception
	{
		log.debug("Group Ironman Tracker started");
		clearObsoleteConfigState();
		normalizeSavedGroupState();
		progressPanel = new ProgressPanel();
		progressPanel.setCreateGroupAction(this::createGroup);
		progressPanel.setLeaveGroupAction(this::leaveGroup);
		progressPanel.setJoinGroupAction(this::joinGroup);
		progressPanel.setShowMembersAction(this::showGroupMembers);
		progressPanel.setShowAuthCodeAction(this::showGroupAuthCode);
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
			currentLoginSessionStart = Instant.now();
			updateLastKnownPlayerName();
			loginSessionResetPending = false;
			eventTracker.resetLevelBaseline();
			eventTracker.resetBossCountBaseline();
			eventTracker.resetCombatTaskBaseline();
			eventTracker.resetCollectionLogBaseline();
			eventTracker.resetQuestsCompleted();
			eventTracker.resetAchievementDiariesCompleted();
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
			updateLastKnownPlayerName();
			if (loginSessionResetPending)
			{
				currentLoginSessionStart = Instant.now();
				loginSessionResetPending = false;
				eventTracker.clearTrackedEvents();
				eventTracker.resetLevelBaseline();
				eventTracker.resetBossCountBaseline();
				eventTracker.resetCombatTaskBaseline();
				eventTracker.resetCollectionLogBaseline();
				eventTracker.resetQuestsCompleted();
				eventTracker.resetAchievementDiariesCompleted();
			}
			progressPanel.updateStatus("Tracking group activity");
			refreshPanel();
			refreshGroupDetails();
		}
		else if (gameStateChanged.getGameState() == GameState.LOGIN_SCREEN)
		{
			currentLoginSessionStart = Instant.EPOCH;
			loginSessionResetPending = true;
			flushPendingEvents("logout");
			eventTracker.clearTrackedEvents();
			eventTracker.resetLevelBaseline();
			eventTracker.resetBossCountBaseline();
			eventTracker.resetCombatTaskBaseline();
			eventTracker.resetCollectionLogBaseline();
			progressPanel.updateStatus("Waiting for login");
			progressPanel.updateMembers(List.of(), false, "");
			persistedRecentEvents = List.of();
			currentGroupMembers = Set.of();
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
		if (tryCaptureQuestEvent(message, event.getType()))
		{
			return;
		}
		if (tryCaptureAchievementDiaryEvent(message, event.getType()))
		{
			return;
		}
		if (tryCaptureCollectionLogEvent(message, event.getType()))
		{
			return;
		}
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
		Player localPlayer = client.getLocalPlayer();
		String playerName = localPlayer == null ? "Unknown" : localPlayer.getName();
		String itemName;
		long value = 0L;
		String bossName = "";
		if (message.startsWith("Drop: "))
		{
			Matcher matcher = BOSS_DROP_MESSAGE.matcher(message);
			if (!matcher.matches())
			{
				return false;
			}

			itemName = matcher.group(1);
			value = Long.parseLong(matcher.group(2).replace(",", ""));
			bossName = matcher.group(3);
		}
		else
		{
			Matcher namedMatcher = NAMED_BOSS_DROP_MESSAGE.matcher(message);
			if (!namedMatcher.matches() || !messageMatchesLocalPlayer(namedMatcher.group(1), playerName))
			{
				return false;
			}

			itemName = namedMatcher.group(2);
			value = Long.parseLong(namedMatcher.group(3).replace(",", ""));
			bossName = namedMatcher.group(4);
		}

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
			progressPanel.updateStatus(bossName.isBlank() ? "Captured drop" : "Captured drop from " + bossName);
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
			if (quotedMatcher.matches())
			{
				tier = quotedMatcher.group(1).toUpperCase();
				taskName = quotedMatcher.group(2);
			}
			else
			{
				Matcher namedMatcher = NAMED_COMBAT_TASK_MESSAGE.matcher(message);
				if (namedMatcher.matches() && messageMatchesLocalPlayer(namedMatcher.group(1), playerName))
				{
					tier = namedMatcher.group(2) == null ? "" : namedMatcher.group(2).toUpperCase();
					taskName = namedMatcher.group(3);
				}
				else
				{
					NamedCombatTask namedTask = parseNamedCombatTaskBroadcast(message, playerName);
					if (namedTask == null)
					{
						return false;
					}

					tier = namedTask.tier;
					taskName = namedTask.taskName;
				}
			}
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

		Player localPlayer = client.getLocalPlayer();
		String localPlayerName = localPlayer == null ? "" : localPlayer.getName();
		String playerName = matcher.group(1);
		if (localPlayerName.isBlank() || !messageMatchesLocalPlayer(playerName, localPlayerName))
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
	private boolean tryCaptureQuestEvent(String message, ChatMessageType messageType)
	{
		Matcher matcher = QUEST_COMPLETION_MESSAGE.matcher(message);
		if (!matcher.matches())
		{
			return false;
		}

		Player localPlayer = client.getLocalPlayer();
		String playerName = localPlayer == null ? "Unknown" : localPlayer.getName();
		if (playerName.isBlank())
		{
			return false;
		}

		String questName = matcher.group(1).trim();
		List<TrackedEvent> newEvents = eventTracker.captureQuestEvent(
			playerName,
			questName,
			messageType.name()
		);
		if (!newEvents.isEmpty())
		{
			log.debug("Captured {} quest completion events", newEvents.size());
			progressPanel.updateStatus("Captured quest completion");
			refreshPanel();
		}
		return !newEvents.isEmpty();
	}

	private boolean tryCaptureAchievementDiaryEvent(String message, ChatMessageType messageType)
	{
		Matcher matcher = ACHIEVEMENT_DIARY_MESSAGE.matcher(message);
		if (!matcher.matches())
		{
			return false;
		}

		Player localPlayer = client.getLocalPlayer();
		String localPlayerName = localPlayer == null ? "" : localPlayer.getName();
		String playerName = matcher.group(1);
		if (localPlayerName.isBlank() || !messageMatchesLocalPlayer(playerName, localPlayerName))
		{
			return false;
		}

		String tier = matcher.group(2).trim().toUpperCase(Locale.ENGLISH);
		String regionName = matcher.group(3).trim();
		List<TrackedEvent> newEvents = eventTracker.captureAchievementDiaryEvent(
			localPlayerName,
			regionName,
			tier,
			messageType.name()
		);
		if (!newEvents.isEmpty())
		{
			log.debug("Captured {} achievement diary events", newEvents.size());
			progressPanel.updateStatus("Captured achievement diary completion");
			refreshPanel();
		}
		return !newEvents.isEmpty();
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		updateLastKnownPlayerName();

		Instant now = Instant.now();
		if (eventTracker.getPendingCount() > 0 && Duration.between(lastSyncAttempt, now).compareTo(SYNC_INTERVAL) >= 0)
		{
			flushPendingEvents("timer");
		}

		if (activeGroupCode().isBlank() || config.sessionToken().isBlank())
		{
			return;
		}

		if (Duration.between(lastRefreshAttempt, now).compareTo(SYNC_INTERVAL) >= 0)
		{
			lastRefreshAttempt = now;
			refreshPersistedEvents();
		}
	}

	@Provides
	GIMTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GIMTrackerConfig.class);
	}

	private void flushPendingEvents(String reason)
	{
		if (activeGroupCode().isBlank())
		{
			progressPanel.updateStatus("Create or join a group");
			refreshPanel();
			return;
		}

		String sessionToken = config.sessionToken();
		if (sessionToken.isBlank())
		{
			progressPanel.updateStatus("Join with your group auth code");
			refreshPanel();
			return;
		}

		List<TrackedEvent> events = new ArrayList<>(eventTracker.drainPendingEvents());
		if ("logout".equals(reason))
		{
			events.addAll(eventTracker.buildBossSessionSummaryEvents(resolvePlayerName()));
		}
		if (events.isEmpty())
		{
			refreshPanel();
			return;
		}

		String playerName = resolvePlayerName();
		ProgressUploadRequest request = new ProgressUploadRequest(
			activeGroupCode(),
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
				boolean success = syncService.sendEvents(API_BASE_URL, sessionToken, request);
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

		normalizeSavedGroupState();

		progressPanel.updatePendingCount(eventTracker.getPendingCount());
		progressPanel.updateLastSync(syncService.getLastSuccessfulSync());
		progressPanel.updateRecentEvents(buildDisplayEvents());
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			progressPanel.updateGroup(displayGroupName(), activeGroupCode());
		}
		else
		{
			progressPanel.updateGroup("Waiting for login", "");
		}
	}

	private void createGroup()
	{
		if (!ensureLoggedIn("Create Group"))
		{
			return;
		}

		if (!activeGroupCode().isBlank())
		{
			progressPanel.showMessage("Create Group", "You are already in a group. Please leave to create another.");
			return;
		}

		Player localPlayer = client.getLocalPlayer();
		String playerName = localPlayer == null ? "" : localPlayer.getName();
		String groupNameInput = progressPanel.promptForValue("Create Group", "Enter a group name");
		String groupName = sanitizeGroupName(groupNameInput);
		if (groupName == null || groupName.isBlank() || playerName.isBlank())
		{
			return;
		}

		progressPanel.updateStatus("Creating group...");
		syncExecutor.submit(() ->
		{
			try
			{
				AuthenticatedGroupResponse group = syncService.createGroup(
					API_BASE_URL,
					new CreateGroupRequest(groupName, playerName)
				);
				saveAuthenticatedGroup(group);
				refreshPanel();
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
		if (!ensureLoggedIn("Join Group"))
		{
			return;
		}

		Player localPlayer = client.getLocalPlayer();
		String playerName = localPlayer == null ? "" : localPlayer.getName();
		String inviteCode = normalizeGroupAuthCode(progressPanel.promptForSensitiveValue("Join Group", "Enter the group auth code"));
		if (inviteCode == null || inviteCode.isBlank() || playerName.isBlank())
		{
			return;
		}

		progressPanel.updateStatus("Joining group...");
		syncExecutor.submit(() ->
		{
			try
			{
				AuthenticatedGroupResponse group = syncService.joinGroup(
					API_BASE_URL,
					new JoinGroupRequest(inviteCode, playerName)
				);
				saveAuthenticatedGroup(group);
				refreshPanel();
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
		normalizeSavedGroupState();
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			progressPanel.updateMembers(List.of(), false, "");
			persistedRecentEvents = List.of();
			currentGroupMembers = Set.of();
			refreshPanel();
			return;
		}

		String inviteCode = activeGroupCode();
		if (inviteCode.isBlank())
		{
			currentGroupName = "";
			progressPanel.updateGroup("No Group", "");
			progressPanel.updateMembers(List.of(), false, "");
			persistedRecentEvents = List.of();
			currentGroupMembers = Set.of();
			refreshPanel();
			return;
		}

		String sessionToken = config.sessionToken();
		if (sessionToken.isBlank())
		{
			progressPanel.updateMembers(List.of(), false, "");
			persistedRecentEvents = List.of();
			currentGroupMembers = Set.of();
			progressPanel.updateStatus("Join with your group auth code");
			refreshPanel();
			return;
		}

		syncExecutor.submit(() ->
		{
			try
			{
				GroupResponse group = syncService.fetchGroup(API_BASE_URL, sessionToken, inviteCode);
				List<GroupMemberResponse> members = syncService.fetchMembers(API_BASE_URL, sessionToken, inviteCode);
				if (!isActiveGroupSession(inviteCode, sessionToken))
				{
					return;
				}
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
		if (!ensureLoggedIn("Group Members"))
		{
			return;
		}

		String inviteCode = activeGroupCode();
		if (inviteCode.isBlank())
		{
			progressPanel.showMessage("Group Members", "You are not currently in a group.");
			return;
		}

		String sessionToken = config.sessionToken();
		if (sessionToken.isBlank())
		{
			progressPanel.showMessage("Group Members", "Join the group with the group auth code first.");
			return;
		}

		syncExecutor.submit(() ->
		{
			try
			{
				List<GroupMemberResponse> members = syncService.fetchMembers(API_BASE_URL, sessionToken, inviteCode);
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

	private void showGroupAuthCode()
	{
		String groupCode = activeGroupCode();
		if (groupCode.isBlank())
		{
			progressPanel.showMessage("Group Auth Code", "You are not currently in a group.");
			return;
		}

		progressPanel.showGroupAuthCode(groupCode);
	}

	private void leaveGroup()
	{
		if (!ensureLoggedIn("Leave Group"))
		{
			return;
		}

		Player localPlayer = client.getLocalPlayer();
		String playerName = localPlayer == null ? "" : localPlayer.getName();
		String sessionToken = config.sessionToken();
		if (activeGroupCode().isBlank() || playerName.isBlank() || sessionToken.isBlank())
		{
			progressPanel.showMessage("Leave Group", "Join the group with the group auth code first.");
			return;
		}

		boolean confirmed = progressPanel.confirm(
			"Leave Group",
			"Are you sure you want to leave " + displayGroupName() + "?"
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
					sessionToken,
					new LeaveGroupRequest(activeGroupCode(), playerName)
				);
				clearSavedGroup();
				eventTracker.clearTrackedEvents();
				progressPanel.updateGroup("No Group", "");
				progressPanel.updateRecentEvents(List.of());
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
		if (!ensureLoggedIn("Remove Member"))
		{
			return;
		}

		Player localPlayer = client.getLocalPlayer();
		String ownerPlayerName = localPlayer == null ? "" : localPlayer.getName();
		String sessionToken = config.sessionToken();
		if (activeGroupCode().isBlank() || ownerPlayerName.isBlank() || sessionToken.isBlank() || memberName == null || memberName.isBlank())
		{
			progressPanel.showMessage("Remove Member", "Join the group with the group auth code first.");
			return;
		}

		progressPanel.updateStatus("Removing member...");
		syncExecutor.submit(() ->
		{
			try
			{
				syncService.removeGroupMember(
					API_BASE_URL,
					sessionToken,
					new RemoveGroupMemberRequest(activeGroupCode(), ownerPlayerName, memberName)
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
		currentGroupCode = group.getInviteCode() == null ? "" : group.getInviteCode();
		currentGroupName = group.getName() == null ? "" : group.getName();
	}

	private void saveAuthenticatedGroup(AuthenticatedGroupResponse group)
	{
		configManager.setConfiguration("gimtracker", "groupCode", group.getInviteCode());
		configManager.setConfiguration("gimtracker", "sessionToken", group.getSessionToken());
		currentGroupCode = group.getInviteCode() == null ? "" : group.getInviteCode();
		currentGroupName = group.getName() == null ? "" : group.getName();
	}

	private void clearSavedGroup()
	{
		configManager.unsetConfiguration("gimtracker", "groupCode");
		configManager.unsetConfiguration("gimtracker", "groupName");
		configManager.unsetConfiguration("gimtracker", "sessionToken");
		currentGroupCode = "";
		currentGroupName = "";
	}

	private void refreshPersistedEvents()
	{
		String inviteCode = activeGroupCode();
		if (inviteCode.isBlank())
		{
			persistedRecentEvents = List.of();
			refreshPanel();
			return;
		}

		try
		{
			String sessionToken = config.sessionToken();
			if (sessionToken.isBlank())
			{
				persistedRecentEvents = List.of();
				refreshPanel();
				return;
			}

			List<BackendEventResponse> backendEvents = syncService.fetchGroupEvents(API_BASE_URL, sessionToken, inviteCode);
			if (!isActiveGroupSession(inviteCode, sessionToken))
			{
				return;
			}
			persistedRecentEvents = collapsePersistedEvents(backendEvents.stream()
				.map(this::toTrackedEvent)
				.filter(event -> event != null)
				.filter(event -> !"BOSS_KC".equals(event.getType()))
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
		List<TrackedEvent> localEvents = eventTracker.getRecentEvents();
		Set<String> localEventIds = localEvents.stream()
			.map(this::eventId)
			.filter(id -> !id.isBlank())
			.collect(Collectors.toSet());
		List<TrackedEvent> mergedEvents = new ArrayList<>(localEvents);
		mergedEvents.addAll(persistedRecentEvents);
		mergedEvents.sort((left, right) -> compareEventTimestamps(right, left));

		List<TrackedEvent> displayEvents = new ArrayList<>();
		Set<String> seenEventKeys = new HashSet<>();
		Set<String> seenBossKcSessions = new HashSet<>();
		Set<String> seenBossDropKeys = new HashSet<>();
		for (TrackedEvent event : mergedEvents)
		{
			if (persistedRecentEvents.contains(event))
			{
				String eventId = eventId(event);
				if (!eventId.isBlank() && localEventIds.contains(eventId))
				{
					continue;
				}
			}

			if (!seenEventKeys.add(eventKey(event)))
			{
				continue;
			}

			if ("BOSS_KC".equals(event.getType()) || "BOSS_KC_SESSION".equals(event.getType()))
			{
				if (!seenBossKcSessions.add(bossKcSessionKey(event)))
				{
					continue;
				}
			}
			else if ("BOSS_DROP".equals(event.getType()))
			{
				if (!seenBossDropKeys.add(bossDropKey(event)))
				{
					continue;
				}
			}

			displayEvents.add(event);
			if (displayEvents.size() >= MAX_DISPLAY_EVENTS)
			{
				break;
			}
		}

		return displayEvents;
	}

	private int compareEventTimestamps(TrackedEvent left, TrackedEvent right)
	{
		return eventTimestamp(left).compareTo(eventTimestamp(right));
	}

	private String eventId(TrackedEvent event)
	{
		Object eventId = event.getDetails().get("eventId");
		return eventId == null ? "" : String.valueOf(eventId);
	}

	private void updateLastKnownPlayerName()
	{
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer != null && localPlayer.getName() != null && !localPlayer.getName().isBlank())
		{
			lastKnownPlayerName = localPlayer.getName();
		}
	}

	private String resolvePlayerName()
	{
		updateLastKnownPlayerName();
		return lastKnownPlayerName == null || lastKnownPlayerName.isBlank() ? "Unknown" : lastKnownPlayerName;
	}

	private Instant eventTimestamp(TrackedEvent event)
	{
		try
		{
			return Instant.parse(event.getTimestamp());
		}
		catch (Exception ex)
		{
			return Instant.EPOCH;
		}
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

	private boolean messageMatchesLocalPlayer(String message, String localPlayerName)
	{
		if (message == null || localPlayerName == null || localPlayerName.isBlank())
		{
			return false;
		}

		String candidate = message;
		int closingBracketIndex = candidate.lastIndexOf(']');
		if (closingBracketIndex >= 0 && closingBracketIndex + 1 < candidate.length())
		{
			candidate = candidate.substring(closingBracketIndex + 1).trim();
		}

		candidate = candidate.replaceFirst("^(?:[A-Z_]+:\\d+\\|)+", "").trim();

		return normalizePlayerIdentifier(candidate).equals(normalizePlayerIdentifier(localPlayerName));
	}

	private String normalizePlayerIdentifier(String playerName)
	{
		if (playerName == null)
		{
			return "";
		}

		return playerName.toLowerCase(Locale.ENGLISH).replaceAll("[^a-z0-9]", "");
	}

	private NamedCombatTask parseNamedCombatTaskBroadcast(String message, String localPlayerName)
	{
		String marker = " has completed ";
		int markerIndex = message.toLowerCase(Locale.ENGLISH).indexOf(marker);
		if (markerIndex <= 0)
		{
			return null;
		}

		String remainder = message.substring(markerIndex + marker.length()).trim();
		if (remainder.endsWith("."))
		{
			remainder = remainder.substring(0, remainder.length() - 1).trim();
		}

		String lowerRemainder = remainder.toLowerCase(Locale.ENGLISH);
		String[] tiers = {"easy", "medium", "hard", "elite", "master", "grandmaster"};
		for (String supportedTier : tiers)
		{
			String[] prefixes = {
				"a " + supportedTier + " combat task: ",
				"an " + supportedTier + " combat task: ",
				"the " + supportedTier + " combat task: ",
				supportedTier + " combat task: "
			};
			for (String prefix : prefixes)
			{
				if (!lowerRemainder.startsWith(prefix))
				{
					continue;
				}

				String taskName = remainder.substring(prefix.length()).trim();
				if (!taskName.isBlank())
				{
					return new NamedCombatTask(supportedTier.toUpperCase(Locale.ENGLISH), taskName);
				}
			}
		}

		return null;
	}

	private static final class NamedCombatTask
	{
		private final String tier;
		private final String taskName;

		private NamedCombatTask(String tier, String taskName)
		{
			this.tier = tier;
			this.taskName = taskName;
		}
	}

	private String eventKey(TrackedEvent event)
	{
		String eventId = eventId(event);
		if (!eventId.isBlank())
		{
			return event.getType() + "|" + eventId;
		}
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
		List<TrackedEvent> orderedEvents = new ArrayList<>(events);
		Collections.reverse(orderedEvents);
		List<TrackedEvent> collapsed = new ArrayList<>();
		Map<String, Integer> activeBossKcIndexes = new LinkedHashMap<>();
		for (TrackedEvent event : orderedEvents)
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

	private String bossKcSessionKey(TrackedEvent event)
	{
		Map<String, Object> details = event.getDetails();
		Object sessionId = details.get("sessionId");
		if (sessionId != null && !String.valueOf(sessionId).isBlank() && !"null".equals(String.valueOf(sessionId)))
		{
			return bossKcStreamKey(event) + "|" + sessionId;
		}

		return eventKey(event);
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

		String previousSessionId = String.valueOf(previousDetails.get("sessionId"));
		String currentSessionId = String.valueOf(currentDetails.get("sessionId"));
		if (previousDetails.containsKey("sessionId") && currentDetails.containsKey("sessionId"))
		{
			return previousSessionId.equals(currentSessionId);
		}

		int previousSessionCount = readInt(previousDetails.get("count"));
		int currentSessionCount = readInt(currentDetails.get("count"));
		int previousTotalCount = readInt(previousDetails.get("totalCount"));
		int currentTotalCount = readInt(currentDetails.get("totalCount"));

		return currentSessionCount > previousSessionCount && currentTotalCount > previousTotalCount;
	}

	private String bossDropKey(TrackedEvent event)
	{
		Map<String, Object> details = event.getDetails();
		return normalizePlayerIdentifier(String.valueOf(details.get("playerName"))) + "|"
			+ normalizeTextKey(String.valueOf(details.get("bossName"))) + "|"
			+ normalizeTextKey(String.valueOf(details.get("itemName"))) + "|"
			+ String.valueOf(details.get("value"));
	}

	private String normalizeTextKey(String value)
	{
		if (value == null)
		{
			return "";
		}

		return value.trim().toLowerCase(Locale.ENGLISH).replaceAll("[^a-z0-9]", "");
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

	private boolean ensureLoggedIn(String actionName)
	{
		if (client.getGameState() == GameState.LOGGED_IN && client.getLocalPlayer() != null)
		{
			return true;
		}

		progressPanel.showMessage(actionName, "Please log into your RuneLite character first.");
		return false;
	}

	private void normalizeSavedGroupState()
	{
		String groupCode = config.groupCode();
		String sessionToken = config.sessionToken();
		if (groupCode.isBlank())
		{
			currentGroupCode = "";
			currentGroupName = "";
			configManager.unsetConfiguration("gimtracker", "groupName");
			if (!sessionToken.isBlank())
			{
				configManager.unsetConfiguration("gimtracker", "sessionToken");
			}
		}
		else if (currentGroupCode.isBlank())
		{
			currentGroupCode = groupCode;
		}
	}

	private String displayGroupName()
	{
		if (activeGroupCode().isBlank())
		{
			return "No Group";
		}

		return currentGroupName == null || currentGroupName.isBlank() ? "No Group" : currentGroupName;
	}

	private String activeGroupCode()
	{
		return currentGroupCode == null ? "" : currentGroupCode;
	}

	private String sanitizeGroupName(String groupName)
	{
		if (groupName == null)
		{
			return null;
		}

		String normalized = groupName.trim().replaceAll("\\s+", " ");
		if (normalized.isEmpty())
		{
			return "";
		}

		if (normalized.length() > MAX_GROUP_NAME_LENGTH)
		{
			progressPanel.showMessage("Create Group", "Group names must be 25 characters or fewer.");
			return null;
		}

		return normalized;
	}

	private String normalizeGroupAuthCode(String groupCode)
	{
		if (groupCode == null)
		{
			return null;
		}

		String normalized = groupCode.trim()
			.toUpperCase(Locale.ENGLISH)
			.replaceAll("[^A-Z0-9]", "");
		if (normalized.isEmpty())
		{
			return "";
		}

		if (normalized.length() != GROUP_AUTH_CODE_LENGTH)
		{
			progressPanel.showMessage("Join Group", "Group auth codes must be exactly 8 characters.");
			return null;
		}

		return normalized;
	}

	private boolean isActiveGroupSession(String inviteCode, String sessionToken)
	{
		String activeGroupCode = activeGroupCode();
		String activeSessionToken = config.sessionToken();
		return inviteCode.equals(activeGroupCode) && sessionToken.equals(activeSessionToken);
	}

	private void clearObsoleteConfigState()
	{
		configManager.unsetConfiguration("gimtracker", "groupName");
		configManager.unsetConfiguration("gimtracker", "apiBaseUrl");
		configManager.unsetConfiguration("gimtracker", "developerMode");
		configManager.unsetConfiguration("gimtracker", "greeting");
		configManager.unsetConfiguration("gimtracker", "syncIntervalSeconds");
	}

}
