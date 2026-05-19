package com.dan.gimtracker;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ChatTriggerParser
{
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

	private ChatTriggerParser()
	{
	}

	static Optional<BossKillCountTrigger> parseBossKillCount(String message)
	{
		Matcher matcher = BOSS_COUNT_MESSAGE.matcher(message);
		if (!matcher.matches())
		{
			return Optional.empty();
		}

		String countType = matcher.group(2).equals("completion count") ? "COMPLETION_COUNT" : "KILL_COUNT";
		return Optional.of(new BossKillCountTrigger(
			matcher.group(1),
			countType,
			Integer.parseInt(matcher.group(3).replace(",", ""))
		));
	}

	static Optional<BossDropTrigger> parseBossDrop(String message, String localPlayerName)
	{
		if (message.startsWith("Drop: "))
		{
			Matcher matcher = BOSS_DROP_MESSAGE.matcher(message);
			if (!matcher.matches())
			{
				return Optional.empty();
			}

			return Optional.of(new BossDropTrigger(
				matcher.group(3),
				matcher.group(1),
				Long.parseLong(matcher.group(2).replace(",", ""))
			));
		}

		Matcher namedMatcher = NAMED_BOSS_DROP_MESSAGE.matcher(message);
		if (!namedMatcher.matches() || !messageMatchesLocalPlayer(namedMatcher.group(1), localPlayerName))
		{
			return Optional.empty();
		}

		return Optional.of(new BossDropTrigger(
			namedMatcher.group(4),
			namedMatcher.group(2),
			Long.parseLong(namedMatcher.group(3).replace(",", ""))
		));
	}

	static Optional<CombatTaskTrigger> parseCombatTask(String message, String localPlayerName)
	{
		Matcher directMatcher = COMBAT_TASK_MESSAGE.matcher(message);
		if (directMatcher.matches())
		{
			String tier = directMatcher.group(1) == null ? "" : directMatcher.group(1).toUpperCase(Locale.ENGLISH);
			return Optional.of(new CombatTaskTrigger(directMatcher.group(2), tier));
		}

		Matcher quotedMatcher = COMBAT_TASK_QUOTED_MESSAGE.matcher(message);
		if (quotedMatcher.matches())
		{
			return Optional.of(new CombatTaskTrigger(
				quotedMatcher.group(2),
				quotedMatcher.group(1).toUpperCase(Locale.ENGLISH)
			));
		}

		Matcher namedMatcher = NAMED_COMBAT_TASK_MESSAGE.matcher(message);
		if (namedMatcher.matches() && messageMatchesLocalPlayer(namedMatcher.group(1), localPlayerName))
		{
			String tier = namedMatcher.group(2) == null ? "" : namedMatcher.group(2).toUpperCase(Locale.ENGLISH);
			return Optional.of(new CombatTaskTrigger(namedMatcher.group(3), tier));
		}

		return parseNamedCombatTaskBroadcast(message, localPlayerName);
	}

	static Optional<CollectionLogTrigger> parseCollectionLog(String message, String localPlayerName)
	{
		Matcher matcher = COLLECTION_LOG_MESSAGE.matcher(message);
		if (!matcher.matches())
		{
			return Optional.empty();
		}

		String playerName = matcher.group(1);
		if (localPlayerName == null || localPlayerName.isBlank() || !messageMatchesLocalPlayer(playerName, localPlayerName))
		{
			return Optional.empty();
		}

		return Optional.of(new CollectionLogTrigger(
			playerName,
			matcher.group(2),
			Integer.parseInt(matcher.group(3).replace(",", "")),
			Integer.parseInt(matcher.group(4).replace(",", ""))
		));
	}

	static Optional<QuestTrigger> parseQuest(String message)
	{
		Matcher matcher = QUEST_COMPLETION_MESSAGE.matcher(message);
		if (!matcher.matches())
		{
			return Optional.empty();
		}

		return Optional.of(new QuestTrigger(matcher.group(1).trim()));
	}

	static Optional<AchievementDiaryTrigger> parseAchievementDiary(String message, String localPlayerName)
	{
		Matcher matcher = ACHIEVEMENT_DIARY_MESSAGE.matcher(message);
		if (!matcher.matches())
		{
			return Optional.empty();
		}

		String playerName = matcher.group(1);
		if (localPlayerName == null || localPlayerName.isBlank() || !messageMatchesLocalPlayer(playerName, localPlayerName))
		{
			return Optional.empty();
		}

		return Optional.of(new AchievementDiaryTrigger(
			matcher.group(3).trim(),
			matcher.group(2).trim().toUpperCase(Locale.ENGLISH)
		));
	}

	private static Optional<CombatTaskTrigger> parseNamedCombatTaskBroadcast(String message, String localPlayerName)
	{
		String marker = " has completed ";
		int markerIndex = message.toLowerCase(Locale.ENGLISH).indexOf(marker);
		if (markerIndex <= 0)
		{
			return Optional.empty();
		}

		String playerName = message.substring(0, markerIndex).trim();
		if (!messageMatchesLocalPlayer(playerName, localPlayerName))
		{
			return Optional.empty();
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
					return Optional.of(new CombatTaskTrigger(
						taskName,
						supportedTier.toUpperCase(Locale.ENGLISH)
					));
				}
			}
		}

		return Optional.empty();
	}

	private static boolean messageMatchesLocalPlayer(String message, String localPlayerName)
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

	private static String normalizePlayerIdentifier(String playerName)
	{
		if (playerName == null)
		{
			return "";
		}

		return playerName.toLowerCase(Locale.ENGLISH).replaceAll("[^a-z0-9]", "");
	}

	static final class BossKillCountTrigger
	{
		private final String bossName;
		private final String countType;
		private final int count;

		private BossKillCountTrigger(String bossName, String countType, int count)
		{
			this.bossName = bossName;
			this.countType = countType;
			this.count = count;
		}

		String getBossName()
		{
			return bossName;
		}

		String getCountType()
		{
			return countType;
		}

		int getCount()
		{
			return count;
		}
	}

	static final class BossDropTrigger
	{
		private final String bossName;
		private final String itemName;
		private final long value;

		private BossDropTrigger(String bossName, String itemName, long value)
		{
			this.bossName = bossName;
			this.itemName = itemName;
			this.value = value;
		}

		String getBossName()
		{
			return bossName;
		}

		String getItemName()
		{
			return itemName;
		}

		long getValue()
		{
			return value;
		}
	}

	static final class CombatTaskTrigger
	{
		private final String taskName;
		private final String tier;

		private CombatTaskTrigger(String taskName, String tier)
		{
			this.taskName = taskName;
			this.tier = tier;
		}

		String getTaskName()
		{
			return taskName;
		}

		String getTier()
		{
			return tier;
		}
	}

	static final class CollectionLogTrigger
	{
		private final String playerName;
		private final String itemName;
		private final int unlockedCount;
		private final int totalCount;

		private CollectionLogTrigger(String playerName, String itemName, int unlockedCount, int totalCount)
		{
			this.playerName = playerName;
			this.itemName = itemName;
			this.unlockedCount = unlockedCount;
			this.totalCount = totalCount;
		}

		String getPlayerName()
		{
			return playerName;
		}

		String getItemName()
		{
			return itemName;
		}

		int getUnlockedCount()
		{
			return unlockedCount;
		}

		int getTotalCount()
		{
			return totalCount;
		}
	}

	static final class QuestTrigger
	{
		private final String questName;

		private QuestTrigger(String questName)
		{
			this.questName = questName;
		}

		String getQuestName()
		{
			return questName;
		}
	}

	static final class AchievementDiaryTrigger
	{
		private final String regionName;
		private final String tier;

		private AchievementDiaryTrigger(String regionName, String tier)
		{
			this.regionName = regionName;
			this.tier = tier;
		}

		String getRegionName()
		{
			return regionName;
		}

		String getTier()
		{
			return tier;
		}
	}
}
