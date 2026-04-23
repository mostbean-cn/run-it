package com.github.runit.ui;

import com.github.runit.config.ActionConfig;
import com.github.runit.config.RunItConfig;
import com.github.runit.config.RunItConfigService;
import com.github.runit.ui.settings.EditActionDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class ManageActionsDialog extends DialogWrapper {
    private static final Dimension CENTER_SIZE = new Dimension(760, 520);
    private static final Color PANEL_BACKGROUND = JBColor.namedColor("Panel.background", new Color(0x2B2B2B));
    private static final Color ROW_BACKGROUND = JBColor.namedColor("List.background", new Color(0x1F2225));
    private static final Color BORDER_COLOR = JBColor.namedColor("Borders.color", new Color(0x4A4A4A));
    private static final Color ACCENT_COLOR = JBColor.namedColor("Component.focusedBorderColor", new Color(0x4C84FF));
    private static final int ROW_HEIGHT = 96;

    private final Project project;
    private final RunItConfigService service;
    private final JPanel listPanel;
    private JBLabel titleLabel;
    private int dragSourceIndex = -1;
    private int dragTargetIndex = -1;

    public ManageActionsDialog(Project project, RunItConfigService service) {
        super(project);
        this.project = project;
        this.service = service;
        setTitle("RunIt - 管理操作");
        setSize(800, 600);
        listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(PANEL_BACKGROUND);
        init();
        refreshList();
    }

    private void refreshList() {
        listPanel.removeAll();
        RunItConfig config = service.getConfig();
        int actionCount = config.actions.size();
        if (titleLabel != null) {
            titleLabel.setText("已配置的操作（" + actionCount + " 个）");
        }

        if (config.actions.isEmpty()) {
            listPanel.add(createEmptyState());
        }

        for (int i = 0; i < config.actions.size(); i++) {
            ActionRow row = new ActionRow(config.actions.get(i), i);
            listPanel.add(row);
        }
        listPanel.revalidate();
        listPanel.repaint();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setPreferredSize(CENTER_SIZE);
        panel.setMinimumSize(new Dimension(680, 460));
        panel.setBackground(PANEL_BACKGROUND);

        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setOpaque(false);
        header.setBorder(JBUI.Borders.empty(16, 20, 12, 20));
        titleLabel = new JBLabel();
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        header.add(titleLabel, BorderLayout.WEST);
        header.add(createAddButton(), BorderLayout.EAST);
        panel.add(header, BorderLayout.NORTH);

        JBScrollPane scrollPane = new JBScrollPane(listPanel);
        scrollPane.setBorder(JBUI.Borders.empty(0, 16));
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        panel.add(scrollPane, BorderLayout.CENTER);

        JLabel hint = new JLabel("拖动左侧手柄排序，双击操作可编辑，配置保存到 .runit/runit.toml");
        hint.setForeground(JBColor.GRAY);
        hint.setBorder(JBUI.Borders.empty(12, 20, 16, 20));
        panel.add(hint, BorderLayout.SOUTH);

        return panel;
    }

    @Override
    protected void createDefaultActions() {
        super.createDefaultActions();
        getOKAction().putValue(Action.NAME, "关闭");
    }

    private JButton createAddButton() {
        JButton addButton = new JButton("添加操作", com.intellij.icons.AllIcons.General.Add);
        addButton.setFocusPainted(false);
        addButton.addActionListener(e -> addAction());
        return addButton;
    }

    private JComponent createEmptyState() {
        JPanel emptyPanel = new JPanel(new GridBagLayout());
        emptyPanel.setOpaque(false);
        emptyPanel.setBorder(JBUI.Borders.empty(64, 16));
        emptyPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        emptyPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));

        JBLabel emptyLabel = new JBLabel("暂无操作，点击右上角添加");
        emptyLabel.setForeground(JBColor.GRAY);
        emptyPanel.add(emptyLabel);
        return emptyPanel;
    }

    private void addAction() {
        EditActionDialog dialog = new EditActionDialog(project, null, -1);
        if (dialog.showAndGet()) {
            service.addAction(dialog.getActionConfig());
            refreshList();
        }
    }

    private void editAction(ActionConfig actionConfig, int index) {
        EditActionDialog dialog = new EditActionDialog(project, actionConfig, index);
        if (dialog.showAndGet()) {
            service.updateAction(index, dialog.getActionConfig());
            refreshList();
        }
    }

    private void deleteAction(Component parent, ActionConfig actionConfig, int index) {
        int confirm = Messages.showYesNoDialog(
                project,
                "确定要删除操作 \"" + actionConfig.name + "\" 吗？",
                "确认删除",
                "删除",
                "取消",
                Messages.getQuestionIcon()
        );
        if (confirm == Messages.YES) {
            service.removeAction(index);
            refreshList();
        }
    }

    private int findDropTargetIndex(Point point) {
        RunItConfig config = service.getConfig();
        if (config.actions.isEmpty()) {
            return -1;
        }

        for (Component component : listPanel.getComponents()) {
            if (!(component instanceof ActionRow row)) {
                continue;
            }

            Rectangle bounds = row.getBounds();
            if (point.y < bounds.y + bounds.height / 2) {
                return row.index;
            }
            if (point.y <= bounds.y + bounds.height) {
                return Math.min(row.index + 1, config.actions.size() - 1);
            }
        }
        return config.actions.size() - 1;
    }

    private void setDragTargetIndex(int index) {
        if (dragTargetIndex == index) {
            return;
        }
        dragTargetIndex = index;
        for (Component component : listPanel.getComponents()) {
            if (component instanceof ActionRow row) {
                row.updateBorder();
            }
        }
        listPanel.repaint();
    }

    private Border createRowBorder(boolean hovered, boolean dropTarget) {
        Border dropBorder = BorderFactory.createMatteBorder(2, 0, 0, 0, dropTarget ? ACCENT_COLOR : ROW_BACKGROUND);
        Border hoverBorder = BorderFactory.createMatteBorder(0, 3, 0, 0, hovered ? ACCENT_COLOR : ROW_BACKGROUND);
        Border bottomBorder = BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR);
        return BorderFactory.createCompoundBorder(
                dropBorder,
                BorderFactory.createCompoundBorder(
                        hoverBorder,
                        BorderFactory.createCompoundBorder(bottomBorder, JBUI.Borders.empty(12, 12))
                )
        );
    }

    private boolean isInsideRow(ActionRow row, MouseEvent event) {
        Point point = SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), row);
        return point.x >= 0 && point.y >= 0 && point.x < row.getWidth() && point.y < row.getHeight();
    }

    private void installRowMouseListener(Component component, MouseAdapter listener) {
        if (component instanceof JButton) {
            return;
        }

        component.addMouseListener(listener);
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                installRowMouseListener(child, listener);
            }
        }
    }

    private class ActionRow extends JPanel {
        private final ActionConfig actionConfig;
        private final int index;
        private boolean hovered;

        ActionRow(ActionConfig action, int idx) {
            this.actionConfig = action;
            this.index = idx;
            setLayout(new BorderLayout());
            updateBorder();
            setBackground(ROW_BACKGROUND);
            setAlignmentX(Component.LEFT_ALIGNMENT);
            Dimension rowSize = new Dimension(Integer.MAX_VALUE, JBUIScale.scale(ROW_HEIGHT));
            setMinimumSize(new Dimension(0, JBUIScale.scale(ROW_HEIGHT)));
            setPreferredSize(rowSize);
            setMaximumSize(rowSize);

            MouseAdapter rowMouseListener = new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hovered = true;
                    updateBorder();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    if (isInsideRow(ActionRow.this, e)) {
                        return;
                    }
                    hovered = false;
                    updateBorder();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        editAction(actionConfig, index);
                    }
                }
            };

            JPanel left = new JPanel(new BorderLayout());
            left.setOpaque(false);

            JBLabel dragHandle = new JBLabel("≡");
            dragHandle.setForeground(JBColor.GRAY);
            dragHandle.setToolTipText("拖动排序");
            dragHandle.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            dragHandle.setBorder(JBUI.Borders.emptyRight(12));
            dragHandle.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    dragSourceIndex = index;
                    setDragTargetIndex(index);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    int targetIndex = findDropTargetIndex(SwingUtilities.convertPoint(dragHandle, e.getPoint(), listPanel));
                    if (dragSourceIndex >= 0 && targetIndex >= 0 && targetIndex != dragSourceIndex) {
                        service.moveAction(dragSourceIndex, targetIndex);
                    }
                    dragSourceIndex = -1;
                    dragTargetIndex = -1;
                    refreshList();
                }
            });
            dragHandle.addMouseMotionListener(new MouseAdapter() {
                @Override
                public void mouseDragged(MouseEvent e) {
                    setDragTargetIndex(findDropTargetIndex(SwingUtilities.convertPoint(dragHandle, e.getPoint(), listPanel)));
                }
            });

            JLabel iconLabel = new JLabel(ExecuteAction.resolveIcon(action.icon));
            iconLabel.setBorder(JBUI.Borders.emptyRight(14));
            iconLabel.setVerticalAlignment(SwingConstants.TOP);

            JPanel textPanel = new JPanel(new BorderLayout(0, 6));
            textPanel.setOpaque(false);
            textPanel.setMinimumSize(new Dimension(0, 0));
            JLabel nameLabel = new JLabel(action.name);
            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 14f));

            TruncatedLabel commandLabel = new TruncatedLabel(action.command);
            commandLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            commandLabel.setForeground(JBColor.GRAY);

            textPanel.add(nameLabel, BorderLayout.NORTH);
            textPanel.add(commandLabel, BorderLayout.CENTER);

            JPanel actionInfo = new JPanel(new BorderLayout());
            actionInfo.setOpaque(false);
            actionInfo.setMinimumSize(new Dimension(0, 0));
            actionInfo.add(iconLabel, BorderLayout.WEST);
            actionInfo.add(textPanel, BorderLayout.CENTER);

            left.add(dragHandle, BorderLayout.WEST);
            left.add(actionInfo, BorderLayout.CENTER);
            left.setMinimumSize(new Dimension(0, 0));

            JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            right.setOpaque(false);
            right.setMinimumSize(new Dimension(JBUIScale.scale(280), 0));
            right.setPreferredSize(new Dimension(JBUIScale.scale(280), JBUIScale.scale(40)));

            JButton editBtn = new JButton("编辑", com.intellij.icons.AllIcons.Actions.Properties);
            editBtn.setFocusPainted(false);
            editBtn.addActionListener(e -> editAction(actionConfig, index));

            JButton delBtn = new JButton("删除", com.intellij.icons.AllIcons.General.Remove);
            delBtn.setFocusPainted(false);
            delBtn.setForeground(JBColor.RED);
            delBtn.addActionListener(e -> deleteAction(this, actionConfig, index));

            right.add(editBtn);
            right.add(delBtn);

            add(left, BorderLayout.CENTER);
            add(right, BorderLayout.EAST);
            installRowMouseListener(this, rowMouseListener);
        }

        private void updateBorder() {
            setBorder(createRowBorder(hovered, dragSourceIndex >= 0 && dragTargetIndex == index));
        }
    }

    private static class TruncatedLabel extends JBLabel {
        private final String fullText;

        TruncatedLabel(String text) {
            super("");
            this.fullText = text != null ? text : "";
            setToolTipText(this.fullText);
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension size = super.getPreferredSize();
            return new Dimension(0, size.height);
        }

        @Override
        public Dimension getMinimumSize() {
            return new Dimension(0, getPreferredSize().height);
        }

        @Override
        protected void paintComponent(Graphics g) {
            FontMetrics metrics = g.getFontMetrics(getFont());
            setText(metrics.stringWidth(fullText) > getWidth() ? trimTextToWidth(metrics) : fullText);
            super.paintComponent(g);
        }

        private String trimTextToWidth(FontMetrics metrics) {
            String suffix = "...";
            int availableWidth = getWidth() - metrics.stringWidth(suffix);
            if (availableWidth <= 0) {
                return suffix;
            }

            int end = fullText.length();
            while (end > 0 && metrics.stringWidth(fullText.substring(0, end)) > availableWidth) {
                end--;
            }
            return fullText.substring(0, end) + suffix;
        }
    }
}
