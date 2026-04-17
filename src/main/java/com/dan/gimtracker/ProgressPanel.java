package com.dan.gimtracker;

import com.dan.gimtracker.model.TrackedEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.PluginPanel;

public class ProgressPanel extends PluginPanel
{
	private static final Color PANEL_BACKGROUND = new Color(27, 31, 38);
	private static final Color CARD_BACKGROUND = new Color(40, 46, 57);
	private static final Color CARD_BORDER = new Color(61, 70, 86);
	private static final Color TITLE_COLOR = new Color(233, 236, 240);
	private static final Color BODY_COLOR = new Color(189, 196, 207);
	private static final Color META_COLOR = new Color(146, 156, 170);
	private static final Color BADGE_BACKGROUND = new Color(86, 124, 184);
	private static final Color BADGE_TEXT = new Color(245, 247, 250);
	private static final Color ACTION_BACKGROUND = new Color(58, 66, 79);
	private static final int CARD_ICON_SIZE = 26;
	private static final int CARD_HEIGHT = 58;

	private final JLabel groupNameValue = new JLabel("Not linked");
	private final JPanel recentEventsContainer = new JPanel();
	private Runnable createGroupAction = () -> { };
	private Runnable leaveGroupAction = () -> { };
	private Runnable joinGroupAction = () -> { };
	private Runnable showMembersAction = () -> showMembersDialog();
	private Runnable showAuthCodeAction = () -> { };
	private Runnable authenticateWithAuthCodeAction = () -> { };
	private Consumer<String> removeMemberAction = memberName -> { };
	private Consumer<String> resetMemberAuthCodeAction = memberName -> { };
	private List<GroupMemberView> currentMembers = List.of();
	private boolean canRemoveMembers;
	private String localPlayerName = "";

	public ProgressPanel()
	{
		setLayout(new BorderLayout());
		setBackground(PANEL_BACKGROUND);
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		recentEventsContainer.setLayout(new BoxLayout(recentEventsContainer, BoxLayout.Y_AXIS));
		recentEventsContainer.setBackground(PANEL_BACKGROUND);
		recentEventsContainer.setOpaque(true);
		recentEventsContainer.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
		recentEventsContainer.add(createEmptyStateLabel());

		JScrollPane recentScrollPane = new JScrollPane(recentEventsContainer);
		recentScrollPane.setBorder(BorderFactory.createEmptyBorder());
		recentScrollPane.getViewport().setBackground(PANEL_BACKGROUND);
		recentScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
		recentScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		recentScrollPane.getVerticalScrollBar().setUnitIncrement(16);

		JPanel recentPanel = new JPanel(new BorderLayout());
		recentPanel.setBackground(PANEL_BACKGROUND);
		recentPanel.setBorder(BorderFactory.createTitledBorder("Recent Group Activity"));
		recentPanel.add(recentScrollPane, BorderLayout.CENTER);

		JPanel groupPanel = new JPanel(new BorderLayout(0, 8));
		groupPanel.setBackground(PANEL_BACKGROUND);
		groupPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

		groupNameValue.setForeground(TITLE_COLOR);
		groupNameValue.setHorizontalAlignment(SwingConstants.CENTER);
		groupNameValue.setAlignmentX(Component.CENTER_ALIGNMENT);
		groupNameValue.setFont(groupNameValue.getFont().deriveFont(Font.BOLD, 19f));

		JPanel actionPanel = new JPanel(new GridLayout(1, 0, 6, 0));
		actionPanel.setBackground(PANEL_BACKGROUND);
		JButton createButton = createActionButton("+", "Create a new group", () -> createGroupAction.run());
		JButton leaveButton = createActionButton("-", "Leave the current group", () -> leaveGroupAction.run());
		JButton membersButton = createActionButton("M", "Show current group members", () -> showMembersAction.run());
		JButton authCodeButton = createActionButton("C", "Show my member auth code", () -> showAuthCodeAction.run());
		JButton authenticateButton = createActionButton("A", "Authenticate with a saved member auth code", () -> authenticateWithAuthCodeAction.run());
		JButton joinButton = createActionButton("J", "Join a group with an invite code", () -> joinGroupAction.run());
		actionPanel.add(createButton);
		actionPanel.add(leaveButton);
		actionPanel.add(membersButton);
		actionPanel.add(authCodeButton);
		actionPanel.add(authenticateButton);
		actionPanel.add(joinButton);
		groupPanel.add(groupNameValue, BorderLayout.NORTH);
		groupPanel.add(actionPanel, BorderLayout.SOUTH);

		add(groupPanel, BorderLayout.NORTH);
		add(recentPanel, BorderLayout.CENTER);
	}

	public void updatePendingCount(int pendingCount)
	{
		runOnUiThread(() -> { });
	}

	public void updateLastSync(Instant lastSync)
	{
		runOnUiThread(() -> { });
	}

	public void updateStatus(String status)
	{
		runOnUiThread(() -> { });
	}

	public void updateGroup(String groupName, String inviteCode)
	{
		runOnUiThread(() ->
			groupNameValue.setText(groupName == null || groupName.isBlank() ? "No Group" : groupName)
		);
	}

	public void updateMembers(List<GroupMemberView> members, boolean canRemoveMembers, String localPlayerName)
	{
		runOnUiThread(() ->
		{
			currentMembers = members;
			this.canRemoveMembers = canRemoveMembers;
			this.localPlayerName = localPlayerName == null ? "" : localPlayerName;
		});
	}

	public void setCreateGroupAction(Runnable createGroupAction)
	{
		this.createGroupAction = createGroupAction;
	}

	public void setJoinGroupAction(Runnable joinGroupAction)
	{
		this.joinGroupAction = joinGroupAction;
	}

	public void setLeaveGroupAction(Runnable leaveGroupAction)
	{
		this.leaveGroupAction = leaveGroupAction;
	}

	public void setRemoveMemberAction(Consumer<String> removeMemberAction)
	{
		this.removeMemberAction = removeMemberAction;
	}

	public void setShowMembersAction(Runnable showMembersAction)
	{
		this.showMembersAction = showMembersAction;
	}

	public void setShowAuthCodeAction(Runnable showAuthCodeAction)
	{
		this.showAuthCodeAction = showAuthCodeAction;
	}

	public void setAuthenticateWithAuthCodeAction(Runnable authenticateWithAuthCodeAction)
	{
		this.authenticateWithAuthCodeAction = authenticateWithAuthCodeAction;
	}

	public void setResetMemberAuthCodeAction(Consumer<String> resetMemberAuthCodeAction)
	{
		this.resetMemberAuthCodeAction = resetMemberAuthCodeAction;
	}

	public String promptForValue(String title, String prompt)
	{
		return JOptionPane.showInputDialog(this, prompt, title, JOptionPane.PLAIN_MESSAGE);
	}

	public String promptForSensitiveValue(String title, String prompt)
	{
		JPasswordField field = new JPasswordField();
		JPanel panel = new JPanel(new BorderLayout(0, 8));
		panel.add(new JLabel(prompt), BorderLayout.NORTH);
		panel.add(field, BorderLayout.CENTER);
		int result = JOptionPane.showConfirmDialog(this, panel, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		if (result != JOptionPane.OK_OPTION)
		{
			return null;
		}

		return new String(field.getPassword());
	}

	public boolean confirm(String title, String message)
	{
		return JOptionPane.showConfirmDialog(this, message, title, JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION;
	}

	public void showMessage(String title, String message)
	{
		runOnUiThread(() -> JOptionPane.showMessageDialog(this, message, title, JOptionPane.PLAIN_MESSAGE));
	}

	public void showAuthCode(String playerName, String authCode)
	{
		runOnUiThread(() ->
			JOptionPane.showMessageDialog(
				this,
				playerName + " auth code:\n" + authCode + "\n\nKeep this code safe if you change computers.",
				"Member Auth Code",
				JOptionPane.PLAIN_MESSAGE
			)
		);
	}

	public void showMembersDialog()
	{
		runOnUiThread(() ->
		{
			if (currentMembers.isEmpty())
			{
				JOptionPane.showMessageDialog(this, "No members in this group yet.", "Group Members", JOptionPane.PLAIN_MESSAGE);
				return;
			}

			JPanel membersPanel = new JPanel();
			membersPanel.setLayout(new BoxLayout(membersPanel, BoxLayout.Y_AXIS));
			membersPanel.setBackground(PANEL_BACKGROUND);

			for (GroupMemberView member : currentMembers)
			{
				membersPanel.add(createMemberRow(member));
				membersPanel.add(Box.createVerticalStrut(6));
			}

			JScrollPane scrollPane = new JScrollPane(membersPanel);
			scrollPane.setBorder(BorderFactory.createEmptyBorder());
			scrollPane.getViewport().setBackground(PANEL_BACKGROUND);
			scrollPane.setPreferredSize(new Dimension(280, Math.min(260, currentMembers.size() * 46)));
			JOptionPane.showMessageDialog(this, scrollPane, "Group Members", JOptionPane.PLAIN_MESSAGE);
		});
	}

	public void updateRecentEvents(List<TrackedEvent> events)
	{
		runOnUiThread(() ->
		{
			recentEventsContainer.removeAll();
			recentEventsContainer.setLayout(new GridLayout(0, 1, 0, 8));

			if (events.isEmpty())
			{
				recentEventsContainer.setLayout(new BoxLayout(recentEventsContainer, BoxLayout.Y_AXIS));
				recentEventsContainer.add(createEmptyStateLabel());
				recentEventsContainer.revalidate();
				recentEventsContainer.repaint();
				return;
			}

			for (TrackedEvent event : events)
			{
				recentEventsContainer.add(createEventCard(event));
			}

			recentEventsContainer.revalidate();
			recentEventsContainer.repaint();
		});
	}

	private void runOnUiThread(Runnable action)
	{
		if (SwingUtilities.isEventDispatchThread())
		{
			action.run();
			return;
		}

		SwingUtilities.invokeLater(action);
	}

	private JLabel createEmptyStateLabel()
	{
		JLabel emptyState = new JLabel("No recent events");
		emptyState.setForeground(BODY_COLOR);
		emptyState.setAlignmentX(Component.LEFT_ALIGNMENT);
		return emptyState;
	}

	private JButton createActionButton(String text, String toolTipText, Runnable action)
	{
		JButton button = new JButton(text);
		button.setToolTipText(toolTipText);
		button.setFocusable(false);
		button.setBackground(ACTION_BACKGROUND);
		button.setForeground(TITLE_COLOR);
		button.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
		button.addActionListener(event -> action.run());
		return button;
	}

	private JPanel createMemberRow(GroupMemberView member)
	{
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setBackground(CARD_BACKGROUND);
		row.setBorder(new CompoundBorder(
			BorderFactory.createLineBorder(CARD_BORDER),
			BorderFactory.createEmptyBorder(6, 8, 6, 8)
		));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

		JLabel nameLabel = new JLabel(member.getDisplayName());
		nameLabel.setForeground(TITLE_COLOR);
		row.add(nameLabel, BorderLayout.CENTER);

		boolean removable = canRemoveMembers
			&& !member.getPlayerName().equalsIgnoreCase(localPlayerName)
			&& !"OWNER".equalsIgnoreCase(member.getRole());
		if (removable)
		{
			JButton removeButton = createActionButton("-", "Remove " + member.getPlayerName() + " from the group", () ->
			{
				if (confirm("Remove Member", "Remove " + member.getPlayerName() + " from the group?"))
				{
					removeMemberAction.accept(member.getPlayerName());
				}
			});
			removeButton.setPreferredSize(new Dimension(28, 24));
			row.add(removeButton, BorderLayout.EAST);
		}

		boolean resettable = canRemoveMembers
			&& !member.getPlayerName().equalsIgnoreCase(localPlayerName);
		if (resettable)
		{
			JButton resetButton = createActionButton("C", "Reset auth code for " + member.getPlayerName(), () ->
			{
				if (confirm("Reset Auth Code", "Reset the auth code for " + member.getPlayerName() + "?"))
				{
					resetMemberAuthCodeAction.accept(member.getPlayerName());
				}
			});
			resetButton.setPreferredSize(new Dimension(28, 24));
			JPanel eastPanel = new JPanel();
			eastPanel.setLayout(new BoxLayout(eastPanel, BoxLayout.X_AXIS));
			eastPanel.setBackground(CARD_BACKGROUND);
			Component existingEast = ((BorderLayout) row.getLayout()).getLayoutComponent(BorderLayout.EAST);
			if (existingEast != null)
			{
				row.remove(existingEast);
				eastPanel.add(existingEast);
				eastPanel.add(Box.createHorizontalStrut(4));
			}
			eastPanel.add(resetButton);
			row.add(eastPanel, BorderLayout.EAST);
		}

		return row;
	}

	private JPanel createEventCard(TrackedEvent event)
	{
		JPanel card = new JPanel(new BorderLayout(10, 0));
		card.setBackground(CARD_BACKGROUND);
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(CARD_BORDER),
			BorderFactory.createEmptyBorder(5, 8, 5, 8)
		));
		card.setPreferredSize(new Dimension(0, CARD_HEIGHT));
		card.setMinimumSize(new Dimension(0, CARD_HEIGHT));
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, CARD_HEIGHT));
		card.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel iconLabel = new JLabel(resolveIcon(event));
		iconLabel.setVerticalAlignment(SwingConstants.TOP);
		card.add(iconLabel, BorderLayout.WEST);

		JPanel textPanel = new JPanel();
		textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
		textPanel.setBackground(CARD_BACKGROUND);

		JLabel titleLabel = new JLabel(asHtml(buildTitle(event)));
		titleLabel.setForeground(TITLE_COLOR);
		titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 12f));
		textPanel.add(titleLabel);

		JLabel subtitleLabel = new JLabel(asHtml(buildSubtitle(event)));
		subtitleLabel.setForeground(BODY_COLOR);
		subtitleLabel.setFont(subtitleLabel.getFont().deriveFont(Font.PLAIN, 11f));
		textPanel.add(subtitleLabel);

		String metaText = buildMetaText(event);
		if (metaText != null)
		{
			JLabel metaLabel = new JLabel(asHtml(metaText));
			metaLabel.setForeground(META_COLOR);
			metaLabel.setFont(metaLabel.getFont().deriveFont(Font.PLAIN, 9f));
			textPanel.add(metaLabel);
		}

		card.add(textPanel, BorderLayout.CENTER);

		String badgeText = buildBadgeText(event);
		if (badgeText != null)
		{
			JPanel badgeWrapper = new JPanel();
			badgeWrapper.setLayout(new BoxLayout(badgeWrapper, BoxLayout.Y_AXIS));
			badgeWrapper.setBackground(CARD_BACKGROUND);
			badgeWrapper.setBorder(new EmptyBorder(0, 4, 0, 0));
			badgeWrapper.setOpaque(true);

			JLabel badgeLabel = new JLabel(badgeText);
			badgeLabel.setOpaque(true);
			badgeLabel.setBackground(BADGE_BACKGROUND);
			badgeLabel.setForeground(BADGE_TEXT);
			badgeLabel.setHorizontalAlignment(SwingConstants.CENTER);
			badgeLabel.setVerticalAlignment(SwingConstants.CENTER);
			badgeLabel.setBorder(BorderFactory.createEmptyBorder(1, 4, 1, 4));
			badgeLabel.setPreferredSize(new Dimension(26, 18));
			badgeLabel.setMinimumSize(new Dimension(26, 18));
			badgeLabel.setMaximumSize(new Dimension(26, 18));
			badgeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
			badgeLabel.setFont(badgeLabel.getFont().deriveFont(Font.BOLD, 10f));
			badgeWrapper.add(badgeLabel);
			badgeWrapper.add(Box.createVerticalGlue());
			card.add(badgeWrapper, BorderLayout.EAST);
		}

		return card;
	}

	private String buildTitle(TrackedEvent event)
	{
		Map<String, Object> details = event.getDetails();
		String type = event.getType();
		if ("LEVEL_UP".equals(type))
		{
			return details.get("skill") + " Level Up";
		}
		if ("COLLECTION_LOG".equals(type))
		{
			return String.valueOf(details.get("itemName"));
		}
		if ("COMBAT_TASK_COMPLETE".equals(type))
		{
			return String.valueOf(details.get("taskName"));
		}
		if ("BOSS_DROP".equals(type))
		{
			return String.valueOf(details.get("itemName"));
		}
		if ("BOSS_KC".equals(type))
		{
			return String.valueOf(details.get("bossName"));
		}

		return event.getSummary();
	}

	private String buildSubtitle(TrackedEvent event)
	{
		Map<String, Object> details = event.getDetails();
		String type = event.getType();
		if ("LEVEL_UP".equals(type))
		{
			return "Reached level " + details.get("newLevel");
		}
		if ("COLLECTION_LOG".equals(type))
		{
			return details.get("playerName") + " unlocked " + details.get("unlockedCount") + "/" + details.get("totalCount");
		}
		if ("COMBAT_TASK_COMPLETE".equals(type))
		{
			String tier = String.valueOf(details.get("tier"));
			return tier.isBlank() ? "Combat task complete" : toTitleCase(tier) + " combat task complete";
		}
		if ("BOSS_DROP".equals(type))
		{
			return details.get("bossName") + " drop worth " + formatCoins(details.get("value"));
		}
		if ("BOSS_KC".equals(type))
		{
			String countType = String.valueOf(details.get("countType")).toLowerCase().replace('_', ' ');
			return toTitleCase(countType);
		}

		return event.getSummary();
	}

	private String buildBadgeText(TrackedEvent event)
	{
		if ("BOSS_KC".equals(event.getType()))
		{
			Object count = event.getDetails().get("count");
			if (count instanceof Number)
			{
				return "x" + formatWholeNumber((Number) count);
			}
			if (count != null)
			{
				return "x" + count;
			}

			Object totalCount = event.getDetails().get("totalCount");
			if (totalCount instanceof Number)
			{
				return "x" + formatWholeNumber((Number) totalCount);
			}
		}

		return null;
	}

	private String buildMetaText(TrackedEvent event)
	{
		Object playerName = event.getDetails().get("playerName");
		if (playerName != null && !String.valueOf(playerName).isBlank())
		{
			return String.valueOf(playerName);
		}

		return null;
	}

	private ImageIcon resolveIcon(TrackedEvent event)
	{
		String resourceName;
		String fallbackText;
		Color fallbackColor;
		switch (event.getType())
		{
			case "LEVEL_UP":
				resourceName = "Skills_icon.png";
				fallbackText = "Lv";
				fallbackColor = new Color(88, 145, 94);
				break;
			case "COLLECTION_LOG":
				resourceName = "Collection_log_detail.png";
				fallbackText = "CL";
				fallbackColor = new Color(140, 104, 66);
				break;
			case "COMBAT_TASK_COMPLETE":
				resourceName = "Combat_icon.png";
				fallbackText = "CT";
				fallbackColor = new Color(154, 70, 70);
				break;
			case "BOSS_DROP":
				resourceName = "Coins_detail.png";
				fallbackText = "GP";
				fallbackColor = new Color(166, 142, 54);
				break;
			case "BOSS_KC":
				resourceName = "Slayer_icon.png";
				fallbackText = "KC";
				fallbackColor = new Color(92, 117, 168);
				break;
			default:
				resourceName = null;
				fallbackText = "?";
				fallbackColor = new Color(94, 102, 117);
		}

		if (resourceName != null)
		{
			try
			{
				BufferedImage resourceImage = ImageIO.read(ProgressPanel.class.getResourceAsStream("/" + resourceName));
				if (resourceImage != null)
				{
					return new ImageIcon(resourceImage.getScaledInstance(CARD_ICON_SIZE, CARD_ICON_SIZE, Image.SCALE_SMOOTH));
				}
			}
			catch (IllegalArgumentException | IOException ignored)
			{
				// Falls back to a generated icon until real assets are available in resources.
			}
		}

		return new ImageIcon(createFallbackIcon(fallbackText, fallbackColor));
	}

	private BufferedImage createFallbackIcon(String text, Color backgroundColor)
	{
		BufferedImage image = new BufferedImage(CARD_ICON_SIZE, CARD_ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setColor(backgroundColor);
		graphics.fillRoundRect(0, 0, CARD_ICON_SIZE, CARD_ICON_SIZE, 8, 8);
		graphics.setColor(Color.WHITE);
		graphics.setFont(graphics.getFont().deriveFont(Font.BOLD, 12f));
		graphics.drawString(text, 7, 20);
		graphics.dispose();
		return image;
	}

	private String formatCoins(Object value)
	{
		if (!(value instanceof Number))
		{
			return String.valueOf(value) + " gp";
		}

		return String.format("%,d gp", ((Number) value).longValue());
	}

	private String formatWholeNumber(Number value)
	{
		double asDouble = value.doubleValue();
		long asLong = value.longValue();
		if (Math.abs(asDouble - asLong) < 0.0000001d)
		{
			return Long.toString(asLong);
		}

		return Double.toString(asDouble);
	}

	private String toTitleCase(String text)
	{
		if (text == null || text.isBlank())
		{
			return "";
		}

		String lowerCase = text.toLowerCase();
		String[] parts = lowerCase.split(" ");
		StringBuilder builder = new StringBuilder();
		for (String part : parts)
		{
			if (part.isEmpty())
			{
				continue;
			}

			if (builder.length() > 0)
			{
				builder.append(' ');
			}

			builder.append(Character.toUpperCase(part.charAt(0)));
			builder.append(part.substring(1));
		}
		return builder.toString();
	}

	private String asHtml(String text)
	{
		return "<html><body style='width:128px'>" + text + "</body></html>";
	}

	public static class GroupMemberView
	{
		private final String playerName;
		private final String role;

		public GroupMemberView(String playerName, String role)
		{
			this.playerName = playerName;
			this.role = role;
		}

		public String getPlayerName()
		{
			return playerName;
		}

		public String getRole()
		{
			return role;
		}

		public String getDisplayName()
		{
			return playerName + " (" + role + ")";
		}
	}
}
