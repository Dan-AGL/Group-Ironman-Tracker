package com.dan.gimtracker;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class GIMTrackerPluginLauncher
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(GIMTrackerPlugin.class);
		RuneLite.main(args);
	}
}
