package com.dan.gimtracker;

import com.dan.gimtracker.model.TrackedEvent;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.PluginPanel;

public class ProgressPanel extends PluginPanel
{
	private static final DateTimeFormatter TIME_FORMATTER =
		DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

	private final JLabel lastSyncValue = new JLabel("Never");
	private final JLabel pendingValue = new JLabel("0");
	private final JLabel statusValue = new JLabel("Idle");
	private final JLabel recentValue = new JLabel("No recent events");
	private final JButton addTestEventButton = new JButton("Add Test Level-Up");
	private final JPanel actionPanel = new JPanel(new GridLayout(0, 1, 0, 6));

	// Builds the small Phase 2 sidebar with sync status, queue size, and optional developer test controls.
	public ProgressPanel(Runnable syncNowAction, Runnable addTestEventAction, boolean developerMode)
	{
		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		JPanel summaryPanel = new JPanel(new GridLayout(0, 1, 0, 6));
		summaryPanel.add(new JLabel("Last sync"));
		summaryPanel.add(lastSyncValue);
		summaryPanel.add(new JLabel("Pending changes"));
		summaryPanel.add(pendingValue);
		summaryPanel.add(new JLabel("Status"));
		summaryPanel.add(statusValue);

		JPanel recentPanel = new JPanel(new GridLayout(0, 1, 0, 4));
		recentPanel.setBorder(BorderFactory.createTitledBorder("Recent events"));
		recentPanel.add(recentValue);

		JButton syncButton = new JButton("Sync Now");
		syncButton.addActionListener(event -> syncNowAction.run());
		addTestEventButton.addActionListener(event -> addTestEventAction.run());

		actionPanel.add(syncButton);
		actionPanel.add(addTestEventButton);
		setDeveloperMode(developerMode);

		add(summaryPanel, BorderLayout.NORTH);
		add(recentPanel, BorderLayout.CENTER);
		add(actionPanel, BorderLayout.SOUTH);
	}

	// Refreshes the queue count shown in the sidebar.
	public void updatePendingCount(int pendingCount)
	{
		pendingValue.setText(Integer.toString(pendingCount));
	}

	// Converts the last successful sync timestamp into a short display value.
	public void updateLastSync(Instant lastSync)
	{
		lastSyncValue.setText(lastSync == null ? "Never" : TIME_FORMATTER.format(lastSync));
	}

	// Surfaces the current sync state so testing failures are visible without reading logs.
	public void updateStatus(String status)
	{
		statusValue.setText(status);
	}

	// Renders the latest tracked events in a compact block for quick validation during testing.
	public void updateRecentEvents(List<TrackedEvent> events)
	{
		if (events.isEmpty())
		{
			recentValue.setText("No recent events");
			return;
		}

		StringBuilder builder = new StringBuilder("<html>");
		for (TrackedEvent event : events)
		{
			builder.append(event.getSummary()).append("<br>");
		}
		builder.append("</html>");
		recentValue.setText(builder.toString());
	}

	// Shows or hides temporary test controls based on the current config.
	public void setDeveloperMode(boolean developerMode)
	{
		addTestEventButton.setVisible(developerMode);
		actionPanel.revalidate();
		actionPanel.repaint();
	}
}
