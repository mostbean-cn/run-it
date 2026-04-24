package com.github.runit.ui;

import com.github.runit.config.ActionConfig;
import com.github.runit.config.ActionScope;
import com.github.runit.config.RunItConfigService;
import com.github.runit.config.ScopedAction;
import com.github.runit.i18n.RunItBundle;
import com.github.runit.ui.settings.EditActionDialog;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.InputEvent;

public class RunItToolbarActionGroup extends AnAction {

    public RunItToolbarActionGroup() {
        super(RunItBundle.message("action.toolbar.text"), RunItBundle.message("action.toolbar.description"), RunItIcons.TOOLBAR);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setText(RunItBundle.message("action.toolbar.text"));
        e.getPresentation().setDescription(RunItBundle.message("action.toolbar.description"));
        e.getPresentation().setEnabledAndVisible(true);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        RunItConfigService service = RunItConfigService.getInstance(project);
        java.util.List<ScopedAction> actions = service.getScopedActions();
        boolean hasActions = !actions.isEmpty();
        DefaultActionGroup group = new DefaultActionGroup();
        addActions(group, actions);

        if (hasActions) {
            group.add(Separator.getInstance());
        }

        group.add(new AnAction(RunItBundle.message("action.add.text"), RunItBundle.message("action.add.description"), com.intellij.icons.AllIcons.General.Add) {
            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.BGT;
            }

            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                EditActionDialog dialog = new EditActionDialog(project, null, -1, ActionScope.PROJECT);
                if (dialog.showAndGet()) {
                    service.addAction(dialog.getSelectedScope(), dialog.getActionConfig());
                }
            }
        });

        if (hasActions) {
            group.add(new AnAction(RunItBundle.message("action.manage.text"), RunItBundle.message("action.manage.description"), com.intellij.icons.AllIcons.Actions.Properties) {
                @Override
                public @NotNull ActionUpdateThread getActionUpdateThread() {
                    return ActionUpdateThread.BGT;
                }

                @Override
                public void actionPerformed(@NotNull AnActionEvent e) {
                    ManageActionsDialog dialog = new ManageActionsDialog(project, service);
                    dialog.show();
                }
            });
        }

        ListPopup popup = JBPopupFactory.getInstance()
                .createActionGroupPopup("RunIt", group, e.getDataContext(), JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true);
        InputEvent inputEvent = e.getInputEvent();
        Component component = inputEvent != null ? inputEvent.getComponent() : null;
        if (component != null) {
            popup.showUnderneathOf(component);
        } else {
            popup.showInBestPositionFor(e.getDataContext());
        }
    }

    private void addActions(DefaultActionGroup group, java.util.List<ScopedAction> actions) {
        for (ScopedAction scopedAction : actions) {
            ActionConfig actionConfig = scopedAction.getActionConfig();
            group.add(new ExecuteAction(actionConfig, scopedAction.getIndex()));
        }
    }
}
