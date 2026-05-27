package com.github.runit.ui.settings;

import com.github.runit.config.ActionConfig;
import com.github.runit.config.ActionScope;
import com.github.runit.config.RunItConfigPaths;
import com.github.runit.i18n.RunItBundle;
import com.github.runit.ui.RunItIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class EditActionDialog extends DialogWrapper {
    private static final ActionScope DEFAULT_SCOPE = ActionScope.GLOBAL;
    private final Project project;
    private final JBTextField nameField;
    private final JComboBox<ActionScope> scopeCombo;
    private final JComboBox<RunItIcons.IconCategory> iconCategoryCombo;
    private final JComboBox<IconItem> iconCombo;
    private final JBTextArea commandArea;
    private final JBLabel configHint;

    public EditActionDialog(Project project, ActionConfig config, int index, ActionScope initialScope) {
        super(project);
        this.project = project;
        setTitle(index < 0 ? RunItBundle.message("dialog.edit.add.title") : RunItBundle.message("dialog.edit.edit.title"));

        nameField = new JBTextField(config != null ? config.name : "");
        scopeCombo = new JComboBox<>(ActionScope.values());
        scopeCombo.setSelectedItem(initialScope != null ? initialScope : DEFAULT_SCOPE);
        iconCategoryCombo = new JComboBox<>(RunItIcons.IconCategory.values());
        iconCategoryCombo.setRenderer(new IconCategoryRenderer());
        iconCombo = new JComboBox<>();
        iconCombo.setRenderer(new IconListRenderer());
        initializeIconSelection(config != null ? config.icon : null);
        commandArea = new JBTextArea(config != null ? config.command : "", 6, 40);
        commandArea.setLineWrap(true);
        commandArea.setWrapStyleWord(true);
        configHint = createConfigHint();
        scopeCombo.addActionListener(e -> updateConfigHint());
        iconCategoryCombo.addActionListener(e -> updateIconOptions());

        init();
        updateConfigHint();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = FormBuilder.createFormBuilder()
                .addLabeledComponent(RunItBundle.message("dialog.edit.label.name"), nameField)
                .addLabeledComponent(RunItBundle.message("dialog.edit.label.scope"), scopeCombo)
                .addLabeledComponent(RunItBundle.message("dialog.edit.label.icon"), createIconSelector())
                .addLabeledComponent(RunItBundle.message("dialog.edit.label.command"), JBUI.Panels.simplePanel(new JScrollPane(commandArea)))
                .addComponent(configHint)
                .getPanel();
        panel.setMinimumSize(new Dimension(450, 250));
        return panel;
    }

    private JComponent createIconSelector() {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setOpaque(false);
        iconCategoryCombo.setPreferredSize(new Dimension(120, iconCategoryCombo.getPreferredSize().height));
        panel.add(iconCategoryCombo, BorderLayout.WEST);
        panel.add(iconCombo, BorderLayout.CENTER);
        return panel;
    }

    private JBLabel createConfigHint() {
        JBLabel hint = new JBLabel();
        hint.setForeground(JBColor.GRAY);
        hint.setToolTipText("");
        return hint;
    }

    private void updateConfigHint() {
        ActionScope scope = getSelectedScope();
        String path = RunItConfigPaths.getDisplayPath(project, scope);
        configHint.setText(RunItBundle.message("dialog.edit.hint", scope.getDisplayName(), path));
        configHint.setToolTipText(path);
    }

    private void initializeIconSelection(String iconKey) {
        RunItIcons.IconDefinition definition = RunItIcons.findDefinition(iconKey);
        iconCategoryCombo.setSelectedItem(definition.category());
        populateIconCombo(definition.category());
        selectIcon(definition.key());
    }

    private void updateIconOptions() {
        IconItem selected = (IconItem) iconCombo.getSelectedItem();
        String previousIconKey = selected != null ? selected.key : null;
        RunItIcons.IconCategory category = getSelectedIconCategory();
        populateIconCombo(category);
        if (!selectIcon(previousIconKey) && iconCombo.getItemCount() > 0) {
            iconCombo.setSelectedIndex(0);
        }
    }

    private void populateIconCombo(RunItIcons.IconCategory category) {
        iconCombo.removeAllItems();
        for (RunItIcons.IconDefinition definition : RunItIcons.availableIconDefinitions(category)) {
            iconCombo.addItem(new IconItem(definition.key(), definition.icon()));
        }
    }

    private boolean selectIcon(String iconKey) {
        if (iconKey == null) {
            return false;
        }
        for (int i = 0; i < iconCombo.getItemCount(); i++) {
            if (iconKey.equals(iconCombo.getItemAt(i).key)) {
                iconCombo.setSelectedIndex(i);
                return true;
            }
        }
        return false;
    }

    private RunItIcons.IconCategory getSelectedIconCategory() {
        RunItIcons.IconCategory category = (RunItIcons.IconCategory) iconCategoryCombo.getSelectedItem();
        return category != null ? category : RunItIcons.IconCategory.DEV;
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        if (nameField.getText().trim().isEmpty()) {
            return new ValidationInfo(RunItBundle.message("dialog.edit.validation.name_required"), nameField);
        }
        return null;
    }

    @Override
    protected void doOKAction() {
        String command = commandArea.getText();
        if (hasSuspectedEscapes(command)) {
            int exitCode = com.intellij.openapi.ui.Messages.showYesNoCancelDialog(
                    project,
                    RunItBundle.message("dialog.edit.escape_warning.message"),
                    RunItBundle.message("dialog.edit.escape_warning.title"),
                    RunItBundle.message("dialog.edit.escape_warning.yes"),
                    RunItBundle.message("dialog.edit.escape_warning.no"),
                    RunItBundle.message("dialog.edit.escape_warning.cancel"),
                    com.intellij.openapi.ui.Messages.getQuestionIcon()
            );
            if (exitCode == com.intellij.openapi.ui.Messages.CANCEL) {
                return;
            }
            if (exitCode == com.intellij.openapi.ui.Messages.YES) {
                commandArea.setText(unescapeCommand(command));
            }
        }
        super.doOKAction();
    }

    private boolean hasSuspectedEscapes(String s) {
        if (s == null) return false;
        return s.contains("\\\"") || s.contains("\\\\");
    }

    private String unescapeCommand(String s) {
        if (s == null) return "";
        return s.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    public ActionConfig getActionConfig() {
        IconItem selected = (IconItem) iconCombo.getSelectedItem();
        return new ActionConfig(
                nameField.getText().trim(),
                selected != null ? selected.key : "run",
                commandArea.getText()
        );
    }

    public ActionScope getSelectedScope() {
        ActionScope scope = (ActionScope) scopeCombo.getSelectedItem();
        return scope != null ? scope : DEFAULT_SCOPE;
    }

    private static class IconItem {
        final String key;
        final javax.swing.Icon icon;

        IconItem(String key, javax.swing.Icon icon) {
            this.key = key;
            this.icon = icon;
        }

        @Override
        public String toString() {
            return key;
        }
    }

    private static class IconCategoryRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                       boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof RunItIcons.IconCategory category) {
                setText(RunItBundle.message(category.getMessageKey()));
            }
            return this;
        }
    }

    private static class IconListRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                       boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof IconItem) {
                IconItem item = (IconItem) value;
                setText(RunItBundle.message("icon." + item.key));
                setIcon(item.icon);
            }
            return this;
        }
    }
}
