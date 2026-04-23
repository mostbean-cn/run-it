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
import java.util.Map;

public class EditActionDialog extends DialogWrapper {
    private final Project project;
    private final JBTextField nameField;
    private final JComboBox<ActionScope> scopeCombo;
    private final JComboBox<IconItem> iconCombo;
    private final JBTextArea commandArea;
    private final JBLabel configHint;

    private static final Map<String, javax.swing.Icon> ICON_MAP = RunItIcons.availableIcons();

    public EditActionDialog(Project project, ActionConfig config, int index, ActionScope initialScope) {
        super(project);
        this.project = project;
        setTitle(index < 0 ? RunItBundle.message("dialog.edit.add.title") : RunItBundle.message("dialog.edit.edit.title"));

        nameField = new JBTextField(config != null ? config.name : "");
        scopeCombo = new JComboBox<>(ActionScope.values());
        scopeCombo.setSelectedItem(initialScope != null ? initialScope : ActionScope.PROJECT);
        iconCombo = new JComboBox<>();
        for (Map.Entry<String, javax.swing.Icon> entry : ICON_MAP.entrySet()) {
            iconCombo.addItem(new IconItem(entry.getKey(), entry.getValue()));
        }
        if (config != null && config.icon != null) {
            for (int i = 0; i < iconCombo.getItemCount(); i++) {
                if (iconCombo.getItemAt(i).key.equals(config.icon)) {
                    iconCombo.setSelectedIndex(i);
                    break;
                }
            }
        }
        iconCombo.setRenderer(new IconListRenderer());
        commandArea = new JBTextArea(config != null ? config.command : "", 6, 40);
        commandArea.setLineWrap(true);
        commandArea.setWrapStyleWord(true);
        configHint = createConfigHint();
        scopeCombo.addActionListener(e -> updateConfigHint());

        init();
        updateConfigHint();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = FormBuilder.createFormBuilder()
                .addLabeledComponent(RunItBundle.message("dialog.edit.label.name"), nameField)
                .addLabeledComponent(RunItBundle.message("dialog.edit.label.scope"), scopeCombo)
                .addLabeledComponent(RunItBundle.message("dialog.edit.label.icon"), iconCombo)
                .addLabeledComponent(RunItBundle.message("dialog.edit.label.command"), JBUI.Panels.simplePanel(new JScrollPane(commandArea)))
                .addComponent(configHint)
                .getPanel();
        panel.setMinimumSize(new Dimension(450, 250));
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

    @Override
    protected @Nullable ValidationInfo doValidate() {
        if (nameField.getText().trim().isEmpty()) {
            return new ValidationInfo(RunItBundle.message("dialog.edit.validation.name_required"), nameField);
        }
        return null;
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
        return scope != null ? scope : ActionScope.PROJECT;
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
