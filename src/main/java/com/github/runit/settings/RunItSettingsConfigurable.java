package com.github.runit.settings;

import com.github.runit.i18n.RunItBundle;
import com.intellij.openapi.options.Configurable;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class RunItSettingsConfigurable implements Configurable {
    private JComboBox<RunItLanguageMode> languageCombo;
    private JPanel panel;
    private JLabel languageLabel;
    private JLabel commentLabel;

    @Override
    public @Nls String getDisplayName() {
        return "RunIt";
    }

    @Override
    public @Nullable JComponent createComponent() {
        if (panel == null) {
            languageCombo = new JComboBox<>(RunItLanguageMode.values());
            languageCombo.setRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    if (value instanceof RunItLanguageMode languageMode) {
                        RunItLanguageMode previewMode = getPreviewLanguageMode();
                        setText(RunItBundle.message(previewMode, languageMode.getMessageKey()));
                    }
                    return this;
                }
            });
            languageCombo.addActionListener(e -> updateTexts(getPreviewLanguageMode()));

            languageLabel = new JLabel();
            commentLabel = new JLabel();
            commentLabel.setBorder(JBUI.Borders.emptyTop(4));

            panel = FormBuilder.createFormBuilder()
                    .addLabeledComponent(languageLabel, languageCombo)
                    .addComponent(commentLabel)
                    .addComponentFillVertically(new JPanel(), 0)
                    .getPanel();
        }
        updateTexts(getPreviewLanguageMode());
        return panel;
    }

    @Override
    public boolean isModified() {
        RunItLanguageMode selected = (RunItLanguageMode) languageCombo.getSelectedItem();
        return selected != RunItSettingsService.getInstance().getLanguageMode();
    }

    @Override
    public void apply() {
        RunItLanguageMode languageMode = getPreviewLanguageMode();
        RunItSettingsService.getInstance().setLanguageMode(languageMode);
        updateTexts(languageMode);
    }

    @Override
    public void reset() {
        languageCombo.setSelectedItem(RunItSettingsService.getInstance().getLanguageMode());
        updateTexts(getPreviewLanguageMode());
    }

    @Override
    public void disposeUIResources() {
        languageCombo = null;
        panel = null;
        languageLabel = null;
        commentLabel = null;
    }

    private RunItLanguageMode getPreviewLanguageMode() {
        RunItLanguageMode selected = languageCombo != null ? (RunItLanguageMode) languageCombo.getSelectedItem() : null;
        return selected != null ? selected : RunItSettingsService.getInstance().getLanguageMode();
    }

    private void updateTexts(RunItLanguageMode languageMode) {
        if (languageLabel != null) {
            languageLabel.setText(RunItBundle.message(languageMode, "settings.language.label"));
        }
        if (commentLabel != null) {
            commentLabel.setText(RunItBundle.message(languageMode, "settings.language.comment"));
        }
        if (languageCombo != null) {
            languageCombo.repaint();
        }
    }
}
