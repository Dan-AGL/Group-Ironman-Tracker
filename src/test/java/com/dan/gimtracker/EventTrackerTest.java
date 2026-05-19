package com.dan.gimtracker;

import com.dan.gimtracker.model.TrackedEvent;
import java.lang.reflect.Proxy;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.events.StatChanged;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EventTrackerTest
{
	private static final String PLAYER_NAME = "Danj Dev";
	private static final String SOURCE_CHANNEL = "GAMEMESSAGE";

	@Test
	public void levelUpEventsUseTheFirstObservedStatAsBaseline()
	{
		EventTracker tracker = new EventTracker();
		Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		Client client = clientWithLevels(levels, PLAYER_NAME);

		levels.put(Skill.ATTACK, 49);
		assertTrue(tracker.captureLevelUpEvents(client, statChanged(Skill.ATTACK), 50).isEmpty());

		levels.put(Skill.ATTACK, 50);
		List<TrackedEvent> events = tracker.captureLevelUpEvents(client, statChanged(Skill.ATTACK), 50);

		assertEquals(1, events.size());
		TrackedEvent event = events.get(0);
		assertEquals("LEVEL_UP", event.getType());
		assertEquals(PLAYER_NAME, event.getDetails().get("playerName"));
		assertEquals("ATTACK", event.getDetails().get("skill"));
		assertEquals(49, event.getDetails().get("oldLevel"));
		assertEquals(50, event.getDetails().get("newLevel"));
		assertEquals(1, tracker.getPendingCount());
	}

	@Test
	public void levelUpEventsIgnoreOverallSkill()
	{
		EventTracker tracker = new EventTracker();

		assertTrue(tracker.captureLevelUpEvents(clientWithLevels(new EnumMap<>(Skill.class), PLAYER_NAME), statChanged(Skill.OVERALL), 1).isEmpty());
		assertEquals(0, tracker.getPendingCount());
	}

	@Test
	public void bossKillCountEventsHonorThresholdAndReplaceTheActiveSessionEvent()
	{
		EventTracker tracker = new EventTracker();

		assertTrue(tracker.captureBossKillCountEvent(PLAYER_NAME, "Zulrah", "KILL_COUNT", 10, 3).isEmpty());
		assertTrue(tracker.captureBossKillCountEvent(PLAYER_NAME, "Zulrah", "KILL_COUNT", 11, 3).isEmpty());

		List<TrackedEvent> thresholdEvent =
			tracker.captureBossKillCountEvent(PLAYER_NAME, "Zulrah", "KILL_COUNT", 12, 3);

		assertEquals(1, thresholdEvent.size());
		assertEquals(3, thresholdEvent.get(0).getDetails().get("count"));
		assertEquals(12, thresholdEvent.get(0).getDetails().get("totalCount"));
		assertEquals(1, tracker.getPendingCount());

		List<TrackedEvent> replacementEvent =
			tracker.captureBossKillCountEvent(PLAYER_NAME, "Zulrah", "KILL_COUNT", 13, 3);

		assertEquals(1, replacementEvent.size());
		assertEquals(4, replacementEvent.get(0).getDetails().get("count"));
		assertEquals(13, replacementEvent.get(0).getDetails().get("totalCount"));
		assertEquals(1, tracker.getPendingCount());
		assertEquals(4, tracker.drainPendingEvents().get(0).getDetails().get("count"));
	}

	@Test
	public void bossDropEventsHonorThresholdAndReplaceDuplicateDrops()
	{
		EventTracker tracker = new EventTracker();

		assertTrue(tracker.captureBossDropEvent("Vorkath", "Draconic visage", 49999, PLAYER_NAME, SOURCE_CHANNEL, 50000).isEmpty());

		List<TrackedEvent> firstDrop =
			tracker.captureBossDropEvent("Vorkath", "Draconic visage", 50000, PLAYER_NAME, SOURCE_CHANNEL, 50000);
		List<TrackedEvent> duplicateDrop =
			tracker.captureBossDropEvent("Vorkath", "Draconic visage", 50000, PLAYER_NAME, SOURCE_CHANNEL, 50000);

		assertEquals(1, firstDrop.size());
		assertEquals(1, duplicateDrop.size());
		assertEquals(1, tracker.getPendingCount());
		assertEquals("BOSS_DROP", tracker.drainPendingEvents().get(0).getType());
	}

	@Test
	public void completionStyleEventsAreOnlyQueuedOncePerSessionKey()
	{
		EventTracker tracker = new EventTracker();

		assertOneEvent("COMBAT_TASK_COMPLETE",
			tracker.captureCombatTaskEvent(PLAYER_NAME, "Perfect Theatre", "GRANDMASTER", SOURCE_CHANNEL));
		assertTrue(tracker.captureCombatTaskEvent(PLAYER_NAME, "Perfect Theatre", "GRANDMASTER", SOURCE_CHANNEL).isEmpty());

		assertOneEvent("QUEST_COMPLETE",
			tracker.captureQuestEvent(PLAYER_NAME, "Dragon Slayer II", SOURCE_CHANNEL));
		assertTrue(tracker.captureQuestEvent(PLAYER_NAME, "Dragon Slayer II", SOURCE_CHANNEL).isEmpty());

		assertOneEvent("ACHIEVEMENT_DIARY_COMPLETE",
			tracker.captureAchievementDiaryEvent(PLAYER_NAME, "Karamja", "HARD", SOURCE_CHANNEL));
		assertTrue(tracker.captureAchievementDiaryEvent(PLAYER_NAME, "Karamja", "HARD", SOURCE_CHANNEL).isEmpty());

		assertOneEvent("COLLECTION_LOG",
			tracker.captureCollectionLogEvent(PLAYER_NAME, "Abyssal orphan", 1, 12, SOURCE_CHANNEL));
		assertTrue(tracker.captureCollectionLogEvent(PLAYER_NAME, "Abyssal orphan", 1, 12, SOURCE_CHANNEL).isEmpty());

		assertEquals(4, tracker.getPendingCount());
	}

	private static void assertOneEvent(String expectedType, List<TrackedEvent> events)
	{
		assertEquals(1, events.size());
		assertEquals(expectedType, events.get(0).getType());
	}

	private static StatChanged statChanged(Skill skill)
	{
		return new StatChanged(skill, 0, 0, 0);
	}

	private static Client clientWithLevels(Map<Skill, Integer> levels, String playerName)
	{
		Player player = playerNamed(playerName);
		return (Client) Proxy.newProxyInstance(
			Client.class.getClassLoader(),
			new Class<?>[] {Client.class},
			(proxy, method, args) ->
			{
				if ("getRealSkillLevel".equals(method.getName()))
				{
					return levels.getOrDefault((Skill) args[0], 1);
				}
				if ("getLocalPlayer".equals(method.getName()))
				{
					return player;
				}

				return defaultValue(proxy, method, args);
			}
		);
	}

	private static Player playerNamed(String playerName)
	{
		return (Player) Proxy.newProxyInstance(
			Player.class.getClassLoader(),
			new Class<?>[] {Player.class},
			(proxy, method, args) ->
			{
				if ("getName".equals(method.getName()))
				{
					return playerName;
				}

				return defaultValue(proxy, method, args);
			}
		);
	}

	private static Object defaultValue(Object proxy, java.lang.reflect.Method method, Object[] args)
	{
		if ("toString".equals(method.getName()))
		{
			return proxy.getClass().getInterfaces()[0].getSimpleName() + "Stub";
		}
		if ("hashCode".equals(method.getName()))
		{
			return System.identityHashCode(proxy);
		}
		if ("equals".equals(method.getName()))
		{
			return proxy == args[0];
		}

		Class<?> returnType = method.getReturnType();
		if (returnType == Void.TYPE)
		{
			return null;
		}
		if (returnType == Boolean.TYPE)
		{
			return false;
		}
		if (returnType == Byte.TYPE)
		{
			return (byte) 0;
		}
		if (returnType == Short.TYPE)
		{
			return (short) 0;
		}
		if (returnType == Integer.TYPE)
		{
			return 0;
		}
		if (returnType == Long.TYPE)
		{
			return 0L;
		}
		if (returnType == Float.TYPE)
		{
			return 0f;
		}
		if (returnType == Double.TYPE)
		{
			return 0d;
		}
		if (returnType == Character.TYPE)
		{
			return (char) 0;
		}
		return null;
	}
}
