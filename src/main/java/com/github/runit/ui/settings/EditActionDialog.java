package com.github.runit.ui.settings;

import com.github.runit.config.ActionConfig;
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
    private final JBTextField nameField;
    private final JComboBox<IconItem> iconCombo;
    private final JBTextArea commandArea;
    private final ActionConfig original;
    private final int index;

    private static final Map<String, javax.swing.Icon> ICON_MAP = RunItIcons.availableIcons();

    public EditActionDialog(Project project, ActionConfig config, int index) {
        super(project);
        this.original = config;
        this.index = index;
        setTitle(index < 0 ? "添加操作" : "编辑操作");

        nameField = new JBTextField(config != null ? config.name : "");
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

        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = FormBuilder.createFormBuilder()
                .addLabeledComponent("名称:", nameField)
                .addLabeledComponent("图标:", iconCombo)
                .addLabeledComponent("要运行的命令:", JBUI.Panels.simplePanel(new JScrollPane(commandArea)))
                .addComponent(createConfigHint())
                .getPanel();
        panel.setMinimumSize(new Dimension(450, 250));
        return panel;
    }

    private JBLabel createConfigHint() {
        JBLabel hint = new JBLabel("配置保存到.runit/runit.toml");
        hint.setForeground(JBColor.GRAY);
        return hint;
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        if (nameField.getText().trim().isEmpty()) {
            return new ValidationInfo("名称不能为空", nameField);
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
                setText(item.key);
                setIcon(item.icon);
            }
            return this;
        }
    }
}
