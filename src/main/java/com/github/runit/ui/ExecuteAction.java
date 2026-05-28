package com.github.runit.ui;

import com.github.runit.config.ActionConfig;
import com.github.runit.executor.CommandExecutor;
import com.github.runit.i18n.RunItBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ExecuteAction extends AnAction {
    private final ActionConfig actionConfig;
    private final int index;

    public ExecuteAction(ActionConfig actionConfig, int index) {
        super(actionConfig.name, RunItBundle.message("action.execute.description", actionConfig.name), resolveIcon(actionConfig.icon));
        this.actionConfig = actionConfig;
        this.index = index;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(!actionConfig.isDamaged());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        if (actionConfig.isDamaged()) {
            return;
        }
        Project project = e.getProject();
        if (project != null) {
            CommandExecutor.execute(project, actionConfig);
        }
    }

    public ActionConfig getActionConfig() {
        return actionConfig;
    }

    public int getIndex() {
        return index;
    }

    public static Icon resolveIcon(String iconName) {
        return RunItIcons.resolve(iconName);
    }
}
