package com.github.runit.config;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.github.runit.i18n.RunItBundle;
import com.moandjiezana.toml.Toml;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class RunItConfigService implements Disposable {
    private static final Logger LOG = Logger.getInstance(RunItConfigService.class);

    private final Project project;
    private volatile RunItConfig globalConfig;
    private volatile RunItActionOrderConfig actionOrderConfig;
    private final Object configLock = new Object();

    public RunItConfigService(Project project) {
        this.project = project;
        setupFileWatcher();
        ApplicationManager.getApplication().executeOnPooledThread(this::ensureConfigLoaded);
    }

    public static RunItConfigService getInstance(Project project) {
        return project.getService(RunItConfigService.class);
    }

    private void ensureConfigLoaded() {
        if (globalConfig != null && actionOrderConfig != null) {
            return;
        }
        synchronized (configLock) {
            if (globalConfig == null) {
                globalConfig = loadConfigInternal(ActionScope.GLOBAL);
            }
            Set<String> usedIds = new LinkedHashSet<>();
            boolean globalDirty = normalizeActions(globalConfig, usedIds);
            if (actionOrderConfig == null) {
                actionOrderConfig = loadActionOrderConfigInternal();
            }
            syncActionOrder(false);
            if (globalDirty) {
                saveConfig(ActionScope.GLOBAL);
            }
        }
    }

    private RunItConfig loadConfigInternal(ActionScope scope) {
        File file = RunItConfigPaths.getConfigFile(project, scope);
        if (file.exists()) {
            try {
                Toml toml = new Toml().read(file);
                RunItConfig loaded = toml.to(RunItConfig.class);
                if (loaded == null) {
                    loaded = new RunItConfig();
                }
                if (loaded.actions == null) {
                    loaded.actions = new ArrayList<>();
                }
                return loaded;
            } catch (Exception e) {
                LOG.warn("Failed to load RunIt config from " + file.getAbsolutePath(), e);
                showConfigLoadErrorNotification(file, scope, e);
            }
        }
        return new RunItConfig();
    }

    private void showConfigLoadErrorNotification(File file, ActionScope scope, Exception e) {
        String scopeName = scope == ActionScope.GLOBAL
                ? RunItBundle.message("scope.global")
                : RunItBundle.message("scope.project");
        String title = RunItBundle.message("notification.config.load_failed.title");
        String content = RunItBundle.message("notification.config.load_failed.content", scopeName, e.getMessage(), file.getAbsolutePath());

        ApplicationManager.getApplication().invokeLater(() -> {
            com.intellij.notification.NotificationGroupManager.getInstance()
                    .getNotificationGroup("RunIt")
                    .createNotification(title, content, com.intellij.notification.NotificationType.ERROR)
                    .notify(project);
        });
    }

    private void reloadConfig(ActionScope scope) {
        synchronized (configLock) {
            globalConfig = loadConfigInternal(ActionScope.GLOBAL);
            normalizeAllActionIds();
            syncActionOrder(false);
        }
    }

    public void reloadAll() {
        synchronized (configLock) {
            globalConfig = loadConfigInternal(ActionScope.GLOBAL);
            actionOrderConfig = loadActionOrderConfigInternal();
            normalizeAllActionIds();
            syncActionOrder(false);
        }
    }

    private RunItActionOrderConfig loadActionOrderConfigInternal() {
        try {
            return RunItActionOrderConfig.load(RunItConfigPaths.getActionOrderFile());
        } catch (Exception e) {
            LOG.warn("Failed to load RunIt action order config", e);
            return new RunItActionOrderConfig();
        }
    }

    private void saveConfig(ActionScope scope) {
        ApplicationManager.getApplication().invokeLater(() -> {
            WriteAction.run(() -> {
                try {
                    RunItConfig config = getConfig(scope);
                    File file = RunItConfigPaths.getConfigFile(project, scope);
                    File dir = file.getParentFile();
                    if (!dir.exists()) {
                        dir.mkdirs();
                    }
                    java.nio.file.Files.write(file.toPath(), config.toToml().getBytes(StandardCharsets.UTF_8));
                    VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
                    if (virtualFile != null) {
                        virtualFile.refresh(false, false);
                    }
                } catch (IOException e) {
                    LOG.error("Failed to save RunIt config for scope " + scope, e);
                }
            });
        });
    }

    public List<ScopedAction> getScopedActions() {
        return getScopedActions(ActionListFilter.PROJECT_RELATED);
    }

    public List<ScopedAction> getScopedActions(ActionListFilter filter) {
        ensureConfigLoaded();
        return applyActionOrder(buildFilteredActions(filter));
    }

    public List<ScopedAction> getScopedActions(ActionScope scope) {
        ensureConfigLoaded();
        List<ScopedAction> actions = new ArrayList<>();
        appendScopedActions(actions, scope, globalConfig, ActionListFilter.ALL);
        return actions;
    }

    public void addAction(ActionScope scope, ActionConfig action) {
        ensureConfigLoaded();
        action.id = generateActionId();
        applyScopeDefaults(action, scope);
        globalConfig.actions.add(action);
        saveConfig(ActionScope.GLOBAL);
        syncActionOrder(true);
    }

    public void updateAction(ActionScope sourceScope, int sourceIndex, ActionScope targetScope, ActionConfig action) {
        ensureConfigLoaded();
        if (sourceIndex < 0 || sourceIndex >= globalConfig.actions.size()) {
            return;
        }

        ActionConfig existingAction = globalConfig.actions.get(sourceIndex);
        action.id = existingAction.id;
        applyScopeDefaults(action, targetScope);

        globalConfig.actions.set(sourceIndex, action);
        saveConfig(ActionScope.GLOBAL);
        syncActionOrder(true);
    }

    public void removeAction(ActionScope scope, int index) {
        ensureConfigLoaded();
        if (index >= 0 && index < globalConfig.actions.size()) {
            globalConfig.actions.remove(index);
            saveConfig(ActionScope.GLOBAL);
            syncActionOrder(true);
        }
    }

    public void moveAction(ActionScope scope, int fromIndex, int toIndex) {
        ensureConfigLoaded();
        int actionCount = globalConfig.actions.size();
        if (fromIndex < 0 || fromIndex >= actionCount || toIndex < 0 || toIndex >= actionCount || fromIndex == toIndex) {
            return;
        }

        ActionConfig action = globalConfig.actions.remove(fromIndex);
        globalConfig.actions.add(toIndex, action);
        saveConfig(ActionScope.GLOBAL);
    }

    public void moveAction(int fromIndex, int toIndex) {
        moveAction(ActionListFilter.PROJECT_RELATED, fromIndex, toIndex);
    }

    public void moveAction(ActionListFilter filter, int fromIndex, int toIndex) {
        ensureConfigLoaded();
        List<String> visibleOrderedIds = resolveActionOrder(buildFilteredActions(filter));
        int actionCount = visibleOrderedIds.size();
        if (fromIndex < 0 || fromIndex >= actionCount || toIndex < 0 || toIndex >= actionCount || fromIndex == toIndex) {
            return;
        }

        String actionId = visibleOrderedIds.remove(fromIndex);
        visibleOrderedIds.add(toIndex, actionId);
        saveActionOrder(mergeVisibleOrder(resolveActionOrder(buildFilteredActions(ActionListFilter.ALL)), visibleOrderedIds));
    }

    private RunItConfig getConfig(ActionScope scope) {
        return globalConfig;
    }

    private void applyScopeDefaults(ActionConfig action, ActionScope scope) {
        ActionScope targetScope = scope != null ? scope : ActionScope.GLOBAL;
        action.scope = targetScope.name();
        if (action.disabledProjectKeys == null) {
            action.disabledProjectKeys = new ArrayList<>();
        }
        if (targetScope == ActionScope.PROJECT) {
            action.projectKey = getProjectOrderKey();
            action.disabledProjectKeys.clear();
        } else {
            action.projectKey = "";
        }
    }

    private ActionScope getActionScope(ActionConfig action) {
        if (ActionScope.PROJECT.name().equalsIgnoreCase(action.scope)) {
            return ActionScope.PROJECT;
        }
        return ActionScope.GLOBAL;
    }

    private boolean isEnabledForCurrentProject(ActionConfig action) {
        ActionScope scope = getActionScope(action);
        String projectKey = getProjectOrderKey();
        if (scope == ActionScope.PROJECT) {
            return projectKey.equals(trimToNull(action.projectKey));
        }
        return action.disabledProjectKeys == null || !action.disabledProjectKeys.contains(projectKey);
    }

    private List<ScopedAction> buildFilteredActions(ActionListFilter filter) {
        List<ScopedAction> actions = new ArrayList<>();
        appendScopedActions(actions, null, globalConfig, filter);
        return actions;
    }

    private void appendScopedActions(List<ScopedAction> target, ActionScope scopeFilter, RunItConfig config, ActionListFilter filter) {
        for (int i = 0; i < config.actions.size(); i++) {
            ActionConfig action = config.actions.get(i);
            ActionScope scope = getActionScope(action);
            if (scopeFilter != null && scope != scopeFilter) {
                continue;
            }
            boolean enabled = isEnabledForCurrentProject(action);
            if (filter == ActionListFilter.PROJECT_RELATED && !enabled) {
                continue;
            }
            if (filter == ActionListFilter.PROJECT_UNRELATED && enabled) {
                continue;
            }
            target.add(new ScopedAction(action, scope, i, enabled));
        }
    }

    private List<ScopedAction> applyActionOrder(List<ScopedAction> currentActions) {
        Map<String, ScopedAction> actionsById = new LinkedHashMap<>();
        for (ScopedAction action : currentActions) {
            actionsById.put(action.getActionConfig().id, action);
        }

        List<ScopedAction> orderedActions = new ArrayList<>();
        for (String actionId : resolveActionOrder(currentActions)) {
            ScopedAction action = actionsById.remove(actionId);
            if (action != null) {
                orderedActions.add(action);
            }
        }
        orderedActions.addAll(actionsById.values());
        return orderedActions;
    }

    private List<String> resolveActionOrder(List<ScopedAction> currentActions) {
        List<String> storedOrder = actionOrderConfig.getActionOrder(getProjectOrderKey());
        Set<String> existingIds = new LinkedHashSet<>();
        for (ScopedAction action : currentActions) {
            existingIds.add(action.getActionConfig().id);
        }

        List<String> resolvedOrder = new ArrayList<>();
        Set<String> includedIds = new LinkedHashSet<>();
        for (String actionId : storedOrder) {
            if (existingIds.contains(actionId) && includedIds.add(actionId)) {
                resolvedOrder.add(actionId);
            }
        }

        for (ScopedAction action : currentActions) {
            String actionId = action.getActionConfig().id;
            if (includedIds.add(actionId)) {
                resolvedOrder.add(actionId);
            }
        }
        return resolvedOrder;
    }

    private List<String> mergeVisibleOrder(List<String> allOrderedIds, List<String> visibleOrderedIds) {
        Set<String> visibleIds = new LinkedHashSet<>(visibleOrderedIds);
        List<String> mergedIds = new ArrayList<>();
        int visibleIndex = 0;
        for (String actionId : allOrderedIds) {
            if (visibleIds.contains(actionId)) {
                mergedIds.add(visibleOrderedIds.get(visibleIndex));
                visibleIndex++;
            } else {
                mergedIds.add(actionId);
            }
        }
        return mergedIds;
    }

    private void syncActionOrder(boolean saveIfChanged) {
        List<String> resolvedOrder = resolveActionOrder(buildFilteredActions(ActionListFilter.ALL));
        List<String> currentOrder = actionOrderConfig.getActionOrder(getProjectOrderKey());
        if (!Objects.equals(resolvedOrder, currentOrder)) {
            actionOrderConfig.setActionOrder(getProjectOrderKey(), resolvedOrder);
            if (saveIfChanged) {
                saveActionOrder(resolvedOrder);
            }
        }
    }

    private void saveActionOrder(List<String> actionIds) {
        actionOrderConfig.setActionOrder(getProjectOrderKey(), actionIds);
        ApplicationManager.getApplication().invokeLater(() -> {
            WriteAction.run(() -> {
                try {
                    File file = RunItConfigPaths.getActionOrderFile();
                    File dir = file.getParentFile();
                    if (!dir.exists()) {
                        dir.mkdirs();
                    }
                    java.nio.file.Files.write(file.toPath(), actionOrderConfig.toToml().getBytes(StandardCharsets.UTF_8));
                    VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
                    if (virtualFile != null) {
                        virtualFile.refresh(false, false);
                    }
                } catch (IOException e) {
                    LOG.error("Failed to save RunIt action order config", e);
                }
            });
        });
    }

    private boolean normalizeAllActionIds() {
        Set<String> usedIds = new LinkedHashSet<>();
        boolean globalDirty = normalizeActions(globalConfig, usedIds);
        if (globalDirty) {
            saveConfig(ActionScope.GLOBAL);
        }
        return globalDirty;
    }

    private boolean normalizeActions(RunItConfig config, Set<String> usedIds) {
        boolean changed = false;
        for (ActionConfig action : config.actions) {
            String normalizedId = trimToNull(action.id);
            if (normalizedId == null || usedIds.contains(normalizedId)) {
                normalizedId = generateActionId();
                changed = true;
            }
            action.id = normalizedId;
            usedIds.add(normalizedId);
            ActionScope scope = getActionScope(action);
            if (!scope.name().equals(action.scope)) {
                action.scope = scope.name();
                changed = true;
            }
            if (action.disabledProjectKeys == null) {
                action.disabledProjectKeys = new ArrayList<>();
                changed = true;
            }
            if (scope == ActionScope.GLOBAL && trimToNull(action.projectKey) != null) {
                action.projectKey = "";
                changed = true;
            }
            if (scope == ActionScope.PROJECT && trimToNull(action.projectKey) == null) {
                action.projectKey = getProjectOrderKey();
                changed = true;
            }
        }
        return changed;
    }

    private String getProjectOrderKey() {
        return RunItConfigPaths.getProjectKey(project);
    }

    private String generateActionId() {
        return UUID.randomUUID().toString();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void setupFileWatcher() {
        project.getMessageBus().connect(this).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
            @Override
            public void after(@NotNull List<? extends VFileEvent> events) {
                for (VFileEvent event : events) {
                    String path = event.getPath();
                    if (path == null) {
                        continue;
                    }
                    if (isConfigPath(path, ActionScope.PROJECT)) {
                        reloadConfig(ActionScope.PROJECT);
                        break;
                    }
                    if (isConfigPath(path, ActionScope.GLOBAL)) {
                        reloadConfig(ActionScope.GLOBAL);
                        break;
                    }
                    if (FileUtil.pathsEqual(path, RunItConfigPaths.getActionOrderFile().getAbsolutePath())) {
                        synchronized (configLock) {
                            actionOrderConfig = loadActionOrderConfigInternal();
                        }
                        break;
                    }
                }
            }
        });
    }

    private boolean isConfigPath(String path, ActionScope scope) {
        return FileUtil.pathsEqual(path, RunItConfigPaths.getConfigFile(project, scope).getAbsolutePath());
    }

    @Override
    public void dispose() {
    }
}
