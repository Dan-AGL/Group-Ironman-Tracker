package com.dan.gimtracker;

import com.dan.gimtracker.model.TrackedEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.events.StatChanged;

public class EventTracker
{
	private static final int MAX_RECENT_EVENTS = 20;

	private final Map<Skill, Integer> lastKnownLevels = new EnumMap<>(Skill.class);
	private final Map<String, Integer> lastReportedBossCounts = new HashMap<>();
	private final Map<String, Integer> sessionBossCounts = new HashMap<>();
	private final Set<String> completedCombatTasks = new HashSet<>();
	private final Set<String> unlockedCollectionLogEntries = new HashSet<>();
	private final List<TrackedEvent> pendingEvents = new ArrayList<>();
	private final Deque<TrackedEvent> recentEvents = new ArrayDeque<>();

	// Clears login state so the next observed stats are treated as the baseline, not as fresh level-ups.
	public void resetLevelBaseline()
	{
		lastKnownLevels.clear();
	}

	// Clears remembered boss counts so a fresh session can establish its own event baseline.
	public void resetBossCountBaseline()
	{
		lastReportedBossCounts.clear();
		sessionBossCounts.clear();
	}

	// Clears remembered combat tasks so a fresh session can establish which completions are new.
	public void resetCombatTaskBaseline()
	{
		completedCombatTasks.clear();
	}

	// Clears remembered collection-log items so a fresh session can establish which unlocks are new.
	public void resetCollectionLogBaseline()
	{
		unlockedCollectionLogEntries.clear();
	}

	// Converts a RuneLite stat change into one or more queued level-up events when the real level increases.
	public List<TrackedEvent> captureLevelUpEvents(Client client, StatChanged event)
	{
		Skill skill = event.getSkill();
		if (skill == Skill.OVERALL)
		{
			return List.of();
		}

		int currentLevel = client.getRealSkillLevel(skill);
		Integer previousLevel = lastKnownLevels.put(skill, currentLevel);

		if (previousLevel == null)
		{
			return List.of();
		}

		if (currentLevel <= previousLevel)
		{
			return List.of();
		}

		String playerName = client.getLocalPlayer() == null ? "Unknown" : client.getLocalPlayer().getName();
		TrackedEvent trackedEvent = TrackedEvent.levelUp(playerName, skill.name(), previousLevel, currentLevel);
		queueEvent(trackedEvent);
		return List.of(trackedEvent);
	}

	// Queues boss KC events once they pass the configured threshold and the count has advanced.
	public List<TrackedEvent> captureBossKillCountEvent(String playerName, String bossName, String countType, int count, int threshold)
	{
		if (threshold <= 0)
		{
			return List.of();
		}

		String bossKey = bossName + "|" + countType;
		Integer previousCount = lastReportedBossCounts.put(bossKey, count);
		if (previousCount == null)
		{
			sessionBossCounts.put(bossKey, 1);
			if (threshold > 1)
			{
				return List.of();
			}
		}
		else if (count <= previousCount)
		{
			return List.of();
		}
		else
		{
			int increment = count - previousCount;
			sessionBossCounts.merge(bossKey, increment, Integer::sum);
		}

		int sessionCount = sessionBossCounts.getOrDefault(bossKey, 0);
		if (sessionCount < threshold)
		{
			return List.of();
		}

		TrackedEvent trackedEvent = TrackedEvent.bossKillCount(playerName, bossName, countType, sessionCount, count);
		queueEvent(trackedEvent);
		return List.of(trackedEvent);
	}

	// Queues boss drop events once they pass the configured value threshold.
	public List<TrackedEvent> captureBossDropEvent(
		String bossName,
		String itemName,
		long value,
		String playerName,
		String sourceChannel,
		int threshold
	)
	{
		if (value < threshold)
		{
			return List.of();
		}

		TrackedEvent trackedEvent = TrackedEvent.bossDrop(bossName, itemName, value, playerName, sourceChannel);
		queueEvent(trackedEvent);
		return List.of(trackedEvent);
	}

	// Queues a combat task completion the first time that task is observed in the current session.
	public List<TrackedEvent> captureCombatTaskEvent(String playerName, String taskName, String tier, String sourceChannel)
	{
		String normalizedTask = taskName.trim();
		if (!completedCombatTasks.add(normalizedTask))
		{
			return List.of();
		}

		TrackedEvent trackedEvent = TrackedEvent.combatTaskComplete(playerName, normalizedTask, tier, sourceChannel);
		queueEvent(trackedEvent);
		return List.of(trackedEvent);
	}

	// Queues a collection-log item the first time that item/count combination is observed in the session.
	public List<TrackedEvent> captureCollectionLogEvent(
		String playerName,
		String itemName,
		int unlockedCount,
		int totalCount,
		String sourceChannel
	)
	{
		String key = playerName.trim() + "|" + itemName.trim() + "|" + unlockedCount + "|" + totalCount;
		if (!unlockedCollectionLogEntries.add(key))
		{
			return List.of();
		}

		TrackedEvent trackedEvent = TrackedEvent.collectionLogItem(
			playerName.trim(),
			itemName.trim(),
			unlockedCount,
			totalCount,
			sourceChannel
		);
		queueEvent(trackedEvent);
		return List.of(trackedEvent);
	}

	// Hands pending events to the sync layer and clears the queue on a best-effort basis.
	public List<TrackedEvent> drainPendingEvents()
	{
		List<TrackedEvent> drained = new ArrayList<>(pendingEvents);
		pendingEvents.clear();
		return drained;
	}

	// Restores failed uploads to the front of the queue so they are retried before newer events.
	public void requeue(List<TrackedEvent> events)
	{
		if (events.isEmpty())
		{
			return;
		}

		pendingEvents.addAll(0, events);
	}

	// Exposes queue depth for the sidebar and timer logic.
	public int getPendingCount()
	{
		return pendingEvents.size();
	}

	// Clears local pending and recent event state when the active group is reset or left.
	public void clearTrackedEvents()
	{
		pendingEvents.clear();
		recentEvents.clear();
	}

	// Returns the latest tracked events for quick validation in the sidebar.
	public List<TrackedEvent> getRecentEvents()
	{
		return new ArrayList<>(recentEvents);
	}

	// Injects a synthetic event so the full queue and sync loop can be tested without in-game actions.
	public void addTestEvent(TrackedEvent trackedEvent)
	{
		queueEvent(trackedEvent);
	}

	// Adds a new event to the pending queue and bounded recent history.
	private void queueEvent(TrackedEvent trackedEvent)
	{
		if ("BOSS_KC".equals(trackedEvent.getType()))
		{
			removeMatchingBossKcEvent(pendingEvents, trackedEvent);
			removeMatchingBossKcEvent(recentEvents, trackedEvent);
		}
		else if ("BOSS_DROP".equals(trackedEvent.getType()))
		{
			removeMatchingBossDropEvent(pendingEvents, trackedEvent);
			removeMatchingBossDropEvent(recentEvents, trackedEvent);
		}

		pendingEvents.add(trackedEvent);
		recentEvents.addFirst(trackedEvent);
		while (recentEvents.size() > MAX_RECENT_EVENTS)
		{
			recentEvents.removeLast();
		}
	}

	private void removeMatchingBossKcEvent(Iterable<TrackedEvent> events, TrackedEvent targetEvent)
	{
		Iterator<TrackedEvent> iterator = events.iterator();
		while (iterator.hasNext())
		{
			TrackedEvent existingEvent = iterator.next();
			if (!"BOSS_KC".equals(existingEvent.getType()))
			{
				continue;
			}

			if (sameBossKcStream(existingEvent, targetEvent))
			{
				iterator.remove();
				return;
			}
		}
	}

	private boolean sameBossKcStream(TrackedEvent first, TrackedEvent second)
	{
		Map<String, Object> firstDetails = first.getDetails();
		Map<String, Object> secondDetails = second.getDetails();
		return String.valueOf(firstDetails.get("playerName")).equals(String.valueOf(secondDetails.get("playerName")))
			&& String.valueOf(firstDetails.get("bossName")).equals(String.valueOf(secondDetails.get("bossName")))
			&& String.valueOf(firstDetails.get("countType")).equals(String.valueOf(secondDetails.get("countType")));
	}

	private void removeMatchingBossDropEvent(Iterable<TrackedEvent> events, TrackedEvent targetEvent)
	{
		Iterator<TrackedEvent> iterator = events.iterator();
		while (iterator.hasNext())
		{
			TrackedEvent existingEvent = iterator.next();
			if (!"BOSS_DROP".equals(existingEvent.getType()))
			{
				continue;
			}

			if (sameBossDrop(existingEvent, targetEvent))
			{
				iterator.remove();
				return;
			}
		}
	}

	private boolean sameBossDrop(TrackedEvent first, TrackedEvent second)
	{
		Map<String, Object> firstDetails = first.getDetails();
		Map<String, Object> secondDetails = second.getDetails();
		return String.valueOf(firstDetails.get("playerName")).equals(String.valueOf(secondDetails.get("playerName")))
			&& String.valueOf(firstDetails.get("itemName")).equals(String.valueOf(secondDetails.get("itemName")))
			&& String.valueOf(firstDetails.get("value")).equals(String.valueOf(secondDetails.get("value")));
	}
}
