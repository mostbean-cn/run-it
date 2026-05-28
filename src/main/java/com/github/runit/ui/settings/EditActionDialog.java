package com.github.runit.ui.settings;

import com.github.runit.config.ActionConfig;
import com.github.runit.config.ActionScope;
import com.github.runit.config.RunItConfigPaths;
import com.github.runit.i18n.RunItBundle;
import com.github.runit.ui.RunItIcons;
import com.intellij.ide.actions.RevealFileAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class EditActionDialog extends DialogWrapper {
    private static final ActionScope DEFAULT_SCOPE = ActionScope.GLOBAL;
    private static final int COMPACT_COMBO_WIDTH = 88;
    private static final int ICON_SELECTOR_WIDTH = 176;
    private final Project project;
    private final JBTextField nameField;
    private final JComboBox<ActionScope> scopeCombo;
    private final JComboBox<RunItIcons.IconCategory> iconCategoryCombo;
    private final JComboBox<IconItem> iconCombo;
    private final JCheckBox disabledForCurrentProjectCheckBox;
    private final JBTextArea commandArea;
    private final JBCheckBox backgroundCheckBox;
    private final JBLabel configHint;
    private final String currentProjectKey;
    private final List<String> disabledProjectKeys;

    public EditActionDialog(Project project, ActionConfig config, int index, ActionScope initialScope) {
        super(project);
        this.project = project;
        this.currentProjectKey = RunItConfigPaths.getProjectKey(project);
        this.disabledProjectKeys = config != null && config.disabledProjectKeys != null
                ? new ArrayList<>(config.disabledProjectKeys)
                : new ArrayList<>();
        setTitle(index < 0 ? RunItBundle.message("dialog.edit.add.title") : RunItBundle.message("dialog.edit.edit.title"));

        nameField = new JBTextField(config != null ? config.name : "");
        scopeCombo = new JComboBox<>(ActionScope.values());
        scopeCombo.setSelectedItem(initialScope != null ? initialScope : DEFAULT_SCOPE);
        disabledForCurrentProjectCheckBox = new JCheckBox(RunItBundle.message("dialog.edit.disable_current_project"));
        disabledForCurrentProjectCheckBox.setSelected(disabledProjectKeys.contains(currentProjectKey));
        iconCategoryCombo = new JComboBox<>(RunItIcons.IconCategory.values());
        iconCategoryCombo.setRenderer(new IconCategoryRenderer());
        iconCombo = new JComboBox<>();
        iconCombo.setRenderer(new IconListRenderer());
        initializeIconSelection(config != null ? config.icon : null);
        commandArea = new JBTextArea(config != null ? config.command : "", 6, 40);
        commandArea.setLineWrap(true);
        commandArea.setWrapStyleWord(true);
        backgroundCheckBox = new JBCheckBox(RunItBundle.message("dialog.edit.label.background"));
        backgroundCheckBox.setSelected(config != null && config.background);
        configHint = createConfigHint();
        scopeCombo.addActionListener(e -> updateScopeState());
        iconCategoryCombo.addActionListener(e -> updateIconOptions());

        init();
        updateScopeState();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = FormBuilder.createFormBuilder()
                .addLabeledComponent(RunItBundle.message("dialog.edit.label.name"), nameField)
                .addLabeledComponent(RunItBundle.message("dialog.edit.label.scope"), createScopeSelector())
                .addLabeledComponent(RunItBundle.message("dialog.edit.label.icon"), createIconSelector())
                .addComponent(backgroundCheckBox)
                .addLabeledComponent(RunItBundle.message("dialog.edit.label.command"), JBUI.Panels.simplePanel(new JScrollPane(commandArea)))
                .addComponent(configHint)
                .getPanel();
        panel.setMinimumSize(new Dimension(450, 250));
        return panel;
    }

    @Override
    protected Action[] createLeftSideActions() {
        return new Action[]{
                new AbstractAction(RunItBundle.message("dialog.edit.open_config_dir")) {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        openConfigFileLocation();
                    }
                }
        };
    }

    private JComponent createScopeSelector() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUIScale.scale(8), 0));
        panel.setOpaque(false);
        setFixedComboWidth(scopeCombo, COMPACT_COMBO_WIDTH);
        panel.add(scopeCombo);
        panel.add(disabledForCurrentProjectCheckBox);
        return panel;
    }

    private JComponent createIconSelector() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUIScale.scale(8), 0));
        panel.setOpaque(false);
        setFixedComboWidth(iconCategoryCombo, COMPACT_COMBO_WIDTH);

        Dimension iconSize = iconCombo.getPreferredSize();
        iconSize.width = JBUIScale.scale(ICON_SELECTOR_WIDTH);
        iconCombo.setMinimumSize(iconSize);
        iconCombo.setPreferredSize(iconSize);
        iconCombo.setMaximumSize(iconSize);

        panel.add(iconCategoryCombo);
        panel.add(iconCombo);
        return panel;
    }

    private static void setFixedComboWidth(JComboBox<?> comboBox, int width) {
        Dimension size = comboBox.getPreferredSize();
        size.width = JBUIScale.scale(width);
        comboBox.setMinimumSize(size);
        comboBox.setPreferredSize(size);
        comboBox.setMaximumSize(size);
    }

    private JBLabel createConfigHint() {
        JBLabel hint = new JBLabel();
        hint.setForeground(JBColor.GRAY);
        hint.setToolTipText("");
        return hint;
    }

    private void openConfigFileLocation() {
        File configFile = RunItConfigPaths.getConfigFile(project, getSelectedScope());
        try {
            ensureConfigFileExists(configFile);
            RevealFileAction.openFile(configFile);
        } catch (IOException | SecurityException e) {
            Messages.showErrorDialog(
                    project,
                    RunItBundle.message("dialog.edit.open_config_dir.error", configFile.getAbsolutePath(), e.getMessage()),
                    RunItBundle.message("dialog.edit.open_config_dir.error.title")
            );
        }
    }

    private void ensureConfigFileExists(File configFile) throws IOException {
        File parent = configFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Failed to create directory: " + parent.getAbsolutePath());
        }
        if (!configFile.exists() && !configFile.createNewFile()) {
            throw new IOException("Failed to create file: " + configFile.getAbsolutePath());
        }
    }

    private void updateConfigHint() {
        ActionScope scope = getSelectedScope();
        String path = RunItConfigPaths.getDisplayPath(project, scope);
        configHint.setText(RunItBundle.message("dialog.edit.hint", scope.getDisplayName(), path));
        configHint.setToolTipText(path);
    }

    private void updateScopeState() {
        boolean globalScope = getSelectedScope() == ActionScope.GLOBAL;
        disabledForCurrentProjectCheckBox.setVisible(globalScope);
        disabledForCurrentProjectCheckBox.setEnabled(globalScope);
        updateConfigHint();
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
            int exitCode = Messages.showYesNoCancelDialog(
                    project,
                    RunItBundle.message("dialog.edit.escape_warning.message"),
                    RunItBundle.message("dialog.edit.escape_warning.title"),
                    RunItBundle.message("dialog.edit.escape_warning.yes"),
                    RunItBundle.message("dialog.edit.escape_warning.no"),
                    RunItBundle.message("dialog.edit.escape_warning.cancel"),
                    Messages.getQuestionIcon()
            );
            if (exitCode == Messages.CANCEL) {
                return;
            }
            if (exitCode == Messages.YES) {
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
        ActionConfig action = new ActionConfig(
                nameField.getText().trim(),
                selected != null ? selected.key : "run",
                commandArea.getText()
        );
        action.background = backgroundCheckBox.isSelected();
        ActionScope selectedScope = getSelectedScope();
        action.scope = selectedScope.name();
        if (selectedScope == ActionScope.GLOBAL) {
            action.disabledProjectKeys = getUpdatedDisabledProjectKeys();
        }
        return action;
    }

    private List<String> getUpdatedDisabledProjectKeys() {
        List<String> updatedProjectKeys = new ArrayList<>(disabledProjectKeys);
        updatedProjectKeys.remove(currentProjectKey);
        if (disabledForCurrentProjectCheckBox.isSelected()) {
            updatedProjectKeys.add(currentProjectKey);
        }
        return updatedProjectKeys;
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
