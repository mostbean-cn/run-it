package com.github.runit.ui;

import com.github.runit.config.ActionConfig;
import com.github.runit.config.RunItConfig;
import com.github.runit.config.RunItConfigService;
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
        super("RunIt", "RunIt script runner", RunItIcons.TOOLBAR);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(true);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        RunItConfigService service = RunItConfigService.getInstance(project);
        RunItConfig config = service.getConfigIfLoaded();
        DefaultActionGroup group = new DefaultActionGroup();

        if (config == null) {
            group.add(new AnAction("加载中...", "Config loading", com.intellij.icons.AllIcons.Actions.Refresh) {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e) {}
            });
            group.add(Separator.getInstance());
        } else {
            for (int i = 0; i < config.actions.size(); i++) {
                ActionConfig actionConfig = config.actions.get(i);
                group.add(new ExecuteAction(actionConfig, i));
            }

            if (!config.actions.isEmpty()) {
                group.add(Separator.getInstance());
            }
        }

        group.add(new AnAction("添加操作", "Add new RunIt action", com.intellij.icons.AllIcons.General.Add) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                EditActionDialog dialog = new EditActionDialog(project, null, -1);
                if (dialog.showAndGet()) {
                    service.addAction(dialog.getActionConfig());
                }
            }
        });

        if (config != null && !config.actions.isEmpty()) {
            group.add(new AnAction("管理操作", "Manage RunIt actions", com.intellij.icons.AllIcons.Actions.Properties) {
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
}
