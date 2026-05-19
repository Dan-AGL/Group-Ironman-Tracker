package com.dan.gimtracker;

import java.util.Optional;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChatTriggerParserTest
{
	private static final String LOCAL_PLAYER = "Danj Dev";

	@Test
	public void parsesBossKillCountMessages()
	{
		Optional<ChatTriggerParser.BossKillCountTrigger> trigger =
			ChatTriggerParser.parseBossKillCount("Your Zulrah kill count is: 1,234.");

		assertTrue(trigger.isPresent());
		assertEquals("Zulrah", trigger.get().getBossName());
		assertEquals("KILL_COUNT", trigger.get().getCountType());
		assertEquals(1234, trigger.get().getCount());
	}

	@Test
	public void parsesBossCompletionCountMessages()
	{
		Optional<ChatTriggerParser.BossKillCountTrigger> trigger =
			ChatTriggerParser.parseBossKillCount("Your Chambers of Xeric completion count is: 37.");

		assertTrue(trigger.isPresent());
		assertEquals("Chambers of Xeric", trigger.get().getBossName());
		assertEquals("COMPLETION_COUNT", trigger.get().getCountType());
		assertEquals(37, trigger.get().getCount());
	}

	@Test
	public void parsesDropMessages()
	{
		Optional<ChatTriggerParser.BossDropTrigger> trigger =
			ChatTriggerParser.parseBossDrop("Drop: Twisted bow (1,200,000,000 coins) from Chambers of Xeric.", LOCAL_PLAYER);

		assertTrue(trigger.isPresent());
		assertEquals("Chambers of Xeric", trigger.get().getBossName());
		assertEquals("Twisted bow", trigger.get().getItemName());
		assertEquals(1200000000L, trigger.get().getValue());
	}

	@Test
	public void parsesNamedDropMessagesForTheLocalPlayer()
	{
		Optional<ChatTriggerParser.BossDropTrigger> trigger = ChatTriggerParser.parseBossDrop(
			"IRONMAN:1|Danj Dev received a drop: Berserker ring (2,500,000 coins) from Dagannoth Rex.",
			LOCAL_PLAYER
		);

		assertTrue(trigger.isPresent());
		assertEquals("Dagannoth Rex", trigger.get().getBossName());
		assertEquals("Berserker ring", trigger.get().getItemName());
		assertEquals(2500000L, trigger.get().getValue());
	}

	@Test
	public void ignoresNamedDropMessagesForOtherPlayers()
	{
		Optional<ChatTriggerParser.BossDropTrigger> trigger = ChatTriggerParser.parseBossDrop(
			"Other Player received a drop: Berserker ring (2,500,000 coins) from Dagannoth Rex.",
			LOCAL_PLAYER
		);

		assertFalse(trigger.isPresent());
	}

	@Test
	public void parsesDirectCombatTaskMessages()
	{
		Optional<ChatTriggerParser.CombatTaskTrigger> trigger = ChatTriggerParser.parseCombatTask(
			"Congratulations, you've completed a grandmaster combat task: Perfect Theatre (6 points).",
			LOCAL_PLAYER
		);

		assertTrue(trigger.isPresent());
		assertEquals("Perfect Theatre", trigger.get().getTaskName());
		assertEquals("GRANDMASTER", trigger.get().getTier());
	}

	@Test
	public void parsesQuotedCombatAchievementMessages()
	{
		Optional<ChatTriggerParser.CombatTaskTrigger> trigger = ChatTriggerParser.parseCombatTask(
			"Congratulations! you've completed an elite combat achievement 'Perfect Olm' (6 points).",
			LOCAL_PLAYER
		);

		assertTrue(trigger.isPresent());
		assertEquals("Perfect Olm", trigger.get().getTaskName());
		assertEquals("ELITE", trigger.get().getTier());
	}

	@Test
	public void parsesNamedCombatTaskMessagesForTheLocalPlayer()
	{
		Optional<ChatTriggerParser.CombatTaskTrigger> trigger = ChatTriggerParser.parseCombatTask(
			"Danj Dev has completed an elite combat achievement task: Perfect Verzik.",
			LOCAL_PLAYER
		);

		assertTrue(trigger.isPresent());
		assertEquals("Perfect Verzik", trigger.get().getTaskName());
		assertEquals("ELITE", trigger.get().getTier());
	}

	@Test
	public void ignoresNamedCombatTaskMessagesForOtherPlayers()
	{
		Optional<ChatTriggerParser.CombatTaskTrigger> trigger = ChatTriggerParser.parseCombatTask(
			"Other Player has completed an elite combat achievement task: Perfect Verzik.",
			LOCAL_PLAYER
		);

		assertFalse(trigger.isPresent());
	}

	@Test
	public void ignoresFallbackCombatTaskBroadcastsForOtherPlayers()
	{
		Optional<ChatTriggerParser.CombatTaskTrigger> trigger = ChatTriggerParser.parseCombatTask(
			"Other Player has completed a grandmaster combat task: Perfect Theatre.",
			LOCAL_PLAYER
		);

		assertFalse(trigger.isPresent());
	}

	@Test
	public void parsesFallbackCombatTaskBroadcastsForTheLocalPlayer()
	{
		Optional<ChatTriggerParser.CombatTaskTrigger> trigger = ChatTriggerParser.parseCombatTask(
			"Danj Dev has completed a grandmaster combat task: Perfect Theatre.",
			LOCAL_PLAYER
		);

		assertTrue(trigger.isPresent());
		assertEquals("Perfect Theatre", trigger.get().getTaskName());
		assertEquals("GRANDMASTER", trigger.get().getTier());
	}

	@Test
	public void parsesCollectionLogMessagesForTheLocalPlayer()
	{
		Optional<ChatTriggerParser.CollectionLogTrigger> trigger = ChatTriggerParser.parseCollectionLog(
			"Danj Dev received a new collection log item: Abyssal orphan (1/12).",
			"Danj_Dev"
		);

		assertTrue(trigger.isPresent());
		assertEquals("Danj Dev", trigger.get().getPlayerName());
		assertEquals("Abyssal orphan", trigger.get().getItemName());
		assertEquals(1, trigger.get().getUnlockedCount());
		assertEquals(12, trigger.get().getTotalCount());
	}

	@Test
	public void ignoresCollectionLogMessagesForOtherPlayers()
	{
		Optional<ChatTriggerParser.CollectionLogTrigger> trigger = ChatTriggerParser.parseCollectionLog(
			"Other Player received a new collection log item: Abyssal orphan (1/12).",
			LOCAL_PLAYER
		);

		assertFalse(trigger.isPresent());
	}

	@Test
	public void parsesQuestCompletionMessages()
	{
		Optional<ChatTriggerParser.QuestTrigger> trigger =
			ChatTriggerParser.parseQuest("Congratulations, you've completed a quest: Dragon Slayer II.");

		assertTrue(trigger.isPresent());
		assertEquals("Dragon Slayer II", trigger.get().getQuestName());
	}

	@Test
	public void parsesAchievementDiaryMessagesForTheLocalPlayer()
	{
		Optional<ChatTriggerParser.AchievementDiaryTrigger> trigger = ChatTriggerParser.parseAchievementDiary(
			"Danj Dev has completed the hard Karamja diary.",
			LOCAL_PLAYER
		);

		assertTrue(trigger.isPresent());
		assertEquals("Karamja", trigger.get().getRegionName());
		assertEquals("HARD", trigger.get().getTier());
	}
}
