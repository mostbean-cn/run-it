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
    private volatile RunItConfig projectConfig;
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
        if (projectConfig != null && globalConfig != null && actionOrderConfig != null) {
            return;
        }
        synchronized (configLock) {
            if (projectConfig == null) {
                projectConfig = loadConfigInternal(ActionScope.PROJECT);
            }
            if (globalConfig == null) {
                globalConfig = loadConfigInternal(ActionScope.GLOBAL);
            }
            boolean projectDirty = normalizeActionIds(projectConfig, new LinkedHashSet<>());
            Set<String> usedIds = new LinkedHashSet<>();
            collectActionIds(projectConfig, usedIds);
            boolean globalDirty = normalizeActionIds(globalConfig, usedIds);
            if (actionOrderConfig == null) {
                actionOrderConfig = loadActionOrderConfigInternal();
            }
            syncActionOrder(false);
            if (projectDirty) {
                saveConfig(ActionScope.PROJECT);
            }
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
            }
        }
        return new RunItConfig();
    }

    private void reloadConfig(ActionScope scope) {
        synchronized (configLock) {
            if (scope == ActionScope.PROJECT) {
                projectConfig = loadConfigInternal(ActionScope.PROJECT);
            } else {
                globalConfig = loadConfigInternal(ActionScope.GLOBAL);
            }
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
        ensureConfigLoaded();
        return applyActionOrder(buildCombinedActions());
    }

    public List<ScopedAction> getScopedActions(ActionScope scope) {
        ensureConfigLoaded();
        List<ScopedAction> actions = new ArrayList<>();
        appendScopedActions(actions, scope, getConfig(scope));
        return actions;
    }

    public void addAction(ActionScope scope, ActionConfig action) {
        ensureConfigLoaded();
        action.id = generateActionId();
        getConfig(scope).actions.add(action);
        saveConfig(scope);
        syncActionOrder(true);
    }

    public void updateAction(ActionScope sourceScope, int sourceIndex, ActionScope targetScope, ActionConfig action) {
        ensureConfigLoaded();
        RunItConfig sourceConfig = getConfig(sourceScope);
        if (sourceIndex < 0 || sourceIndex >= sourceConfig.actions.size()) {
            return;
        }

        ActionConfig existingAction = sourceConfig.actions.get(sourceIndex);
        action.id = existingAction.id;

        if (sourceScope == targetScope) {
            sourceConfig.actions.set(sourceIndex, action);
            saveConfig(sourceScope);
            syncActionOrder(true);
            return;
        }

        sourceConfig.actions.remove(sourceIndex);
        getConfig(targetScope).actions.add(action);
        saveConfig(sourceScope);
        saveConfig(targetScope);
        syncActionOrder(true);
    }

    public void removeAction(ActionScope scope, int index) {
        ensureConfigLoaded();
        RunItConfig config = getConfig(scope);
        if (index >= 0 && index < config.actions.size()) {
            config.actions.remove(index);
            saveConfig(scope);
            syncActionOrder(true);
        }
    }

    public void moveAction(ActionScope scope, int fromIndex, int toIndex) {
        ensureConfigLoaded();
        RunItConfig config = getConfig(scope);
        int actionCount = config.actions.size();
        if (fromIndex < 0 || fromIndex >= actionCount || toIndex < 0 || toIndex >= actionCount || fromIndex == toIndex) {
            return;
        }

        ActionConfig action = config.actions.remove(fromIndex);
        config.actions.add(toIndex, action);
        saveConfig(scope);
    }

    public void moveAction(int fromIndex, int toIndex) {
        ensureConfigLoaded();
        List<String> orderedIds = resolveActionOrder(buildCombinedActions());
        int actionCount = orderedIds.size();
        if (fromIndex < 0 || fromIndex >= actionCount || toIndex < 0 || toIndex >= actionCount || fromIndex == toIndex) {
            return;
        }

        String actionId = orderedIds.remove(fromIndex);
        orderedIds.add(toIndex, actionId);
        saveActionOrder(orderedIds);
    }

    private RunItConfig getConfig(ActionScope scope) {
        return scope == ActionScope.GLOBAL ? globalConfig : projectConfig;
    }

    private List<ScopedAction> buildCombinedActions() {
        List<ScopedAction> actions = new ArrayList<>();
        appendScopedActions(actions, ActionScope.PROJECT, projectConfig);
        appendScopedActions(actions, ActionScope.GLOBAL, globalConfig);
        return actions;
    }

    private void appendScopedActions(List<ScopedAction> target, ActionScope scope, RunItConfig config) {
        for (int i = 0; i < config.actions.size(); i++) {
            target.add(new ScopedAction(config.actions.get(i), scope, i));
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

    private void syncActionOrder(boolean saveIfChanged) {
        List<String> resolvedOrder = resolveActionOrder(buildCombinedActions());
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
        boolean projectDirty = normalizeActionIds(projectConfig, usedIds);
        boolean globalDirty = normalizeActionIds(globalConfig, usedIds);
        if (projectDirty) {
            saveConfig(ActionScope.PROJECT);
        }
        if (globalDirty) {
            saveConfig(ActionScope.GLOBAL);
        }
        return projectDirty || globalDirty;
    }

    private boolean normalizeActionIds(RunItConfig config, Set<String> usedIds) {
        boolean changed = false;
        for (ActionConfig action : config.actions) {
            String normalizedId = trimToNull(action.id);
            if (normalizedId == null || usedIds.contains(normalizedId)) {
                normalizedId = generateActionId();
                changed = true;
            }
            action.id = normalizedId;
            usedIds.add(normalizedId);
        }
        return changed;
    }

    private void collectActionIds(RunItConfig config, Set<String> usedIds) {
        for (ActionConfig action : config.actions) {
            String normalizedId = trimToNull(action.id);
            if (normalizedId != null) {
                usedIds.add(normalizedId);
            }
        }
    }

    private String getProjectOrderKey() {
        String basePath = project.getBasePath();
        if (basePath != null) {
            String canonicalPath = FileUtil.toCanonicalPath(basePath);
            if (canonicalPath != null) {
                return FileUtil.toSystemIndependentName(canonicalPath);
            }
            return FileUtil.toSystemIndependentName(basePath);
        }
        return "workspace:" + project.getLocationHash();
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
