package com.dan.gimtracker;

import com.dan.gimtracker.model.TrackedEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.events.StatChanged;

public class EventTracker
{
	private static final int MAX_RECENT_EVENTS = 5;

	private final Map<Skill, Integer> lastKnownLevels = new EnumMap<>(Skill.class);
	private final Map<String, Integer> lastReportedBossCounts = new HashMap<>();
	private final Set<String> completedCombatTasks = new HashSet<>();
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
	}

	// Clears remembered combat tasks so a fresh session can establish which completions are new.
	public void resetCombatTaskBaseline()
	{
		completedCombatTasks.clear();
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

		TrackedEvent trackedEvent = TrackedEvent.levelUp(skill.name(), previousLevel, currentLevel);
		queueEvent(trackedEvent);
		return List.of(trackedEvent);
	}

	// Queues boss KC events once they pass the configured threshold and the count has advanced.
	public List<TrackedEvent> captureBossKillCountEvent(String bossName, String countType, int count, int threshold)
	{
		if (count < threshold)
		{
			return List.of();
		}

		String bossKey = bossName + "|" + countType;
		Integer previousCount = lastReportedBossCounts.put(bossKey, count);
		if (previousCount != null && count <= previousCount)
		{
			return List.of();
		}

		TrackedEvent trackedEvent = TrackedEvent.bossKillCount(bossName, countType, count);
		queueEvent(trackedEvent);
		return List.of(trackedEvent);
	}

	// Queues boss drop events once they pass the configured value threshold.
	public List<TrackedEvent> captureBossDropEvent(String bossName, String itemName, long value, String sourceChannel, int threshold)
	{
		if (value < threshold)
		{
			return List.of();
		}

		TrackedEvent trackedEvent = TrackedEvent.bossDrop(bossName, itemName, value, sourceChannel);
		queueEvent(trackedEvent);
		return List.of(trackedEvent);
	}

	// Queues a combat task completion the first time that task is observed in the current session.
	public List<TrackedEvent> captureCombatTaskEvent(String taskName, String tier, String sourceChannel)
	{
		String normalizedTask = taskName.trim();
		if (!completedCombatTasks.add(normalizedTask))
		{
			return List.of();
		}

		TrackedEvent trackedEvent = TrackedEvent.combatTaskComplete(normalizedTask, tier, sourceChannel);
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
		pendingEvents.add(trackedEvent);
		recentEvents.addFirst(trackedEvent);
		while (recentEvents.size() > MAX_RECENT_EVENTS)
		{
			recentEvents.removeLast();
		}
	}
}
