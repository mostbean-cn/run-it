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
import java.nio.file.StandardCopyOption;
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
    private volatile boolean globalConfigLoadedPartially;
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
                LoadConfigResult result = loadConfigInternal(ActionScope.GLOBAL);
                globalConfig = result.config();
                globalConfigLoadedPartially = result.partial();
            }
            Set<String> usedIds = new LinkedHashSet<>();
            boolean globalDirty = normalizeActions(globalConfig, usedIds);
            if (actionOrderConfig == null) {
                actionOrderConfig = loadActionOrderConfigInternal();
            }
            syncActionOrder(false);
            if (globalDirty && !globalConfigLoadedPartially) {
                saveConfig(ActionScope.GLOBAL);
            }
        }
    }

    private LoadConfigResult loadConfigInternal(ActionScope scope) {
        File file = RunItConfigPaths.getConfigFile(project, scope);
        if (file.exists()) {
            try {
                return new LoadConfigResult(readConfig(file), false);
            } catch (Exception e) {
                LOG.warn("Failed to load RunIt config from " + file.getAbsolutePath(), e);
                PartialConfigLoadResult partialResult = loadConfigPartially(file);
                if (!partialResult.config().actions.isEmpty()) {
                    return new LoadConfigResult(partialResult.config(), partialResult.skippedCount() > 0);
                }
            }
        }
        return new LoadConfigResult(new RunItConfig(), false);
    }

    private RunItConfig readConfig(File file) {
        Toml toml = new Toml().read(file);
        RunItConfig loaded = toml.to(RunItConfig.class);
        if (loaded == null) {
            loaded = new RunItConfig();
        }
        if (loaded.actions == null) {
            loaded.actions = new ArrayList<>();
        }
        preserveRawTomlBlocks(file, loaded);
        return loaded;
    }

    private void preserveRawTomlBlocks(File file, RunItConfig config) {
        List<String> actionBlocks;
        try {
            actionBlocks = extractActionBlocks(java.nio.file.Files.readString(file.toPath(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOG.warn("Failed to read RunIt config blocks from " + file.getAbsolutePath(), e);
            return;
        }
        if (actionBlocks.size() != config.actions.size()) {
            return;
        }
        for (int i = 0; i < config.actions.size(); i++) {
            preserveRawTomlBlock(config.actions.get(i), actionBlocks.get(i));
        }
    }

    private PartialConfigLoadResult loadConfigPartially(File file) {
        RunItConfig config = new RunItConfig();
        List<String> actionBlocks;
        try {
            actionBlocks = extractActionBlocks(java.nio.file.Files.readString(file.toPath(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOG.warn("Failed to read RunIt config for partial load from " + file.getAbsolutePath(), e);
            return new PartialConfigLoadResult(config, 0, 0);
        }

        int loadedCount = 0;
        int damagedCount = 0;
        int blockIndex = 1;
        for (String actionBlock : actionBlocks) {
            try {
                RunItConfig blockConfig = readConfigFragment(actionBlock);
                if (blockConfig.actions.isEmpty()) {
                    config.actions.add(createDamagedAction(actionBlock, RunItBundle.message("dialog.manage.damaged.empty_block"), blockIndex));
                    damagedCount++;
                    blockIndex++;
                    continue;
                }
                preserveRawTomlBlock(blockConfig, actionBlock);
                config.actions.addAll(blockConfig.actions);
                loadedCount += blockConfig.actions.size();
            } catch (Exception e) {
                RunItConfig recoveredConfig = recoverCommandBlock(actionBlock);
                if (recoveredConfig != null && !recoveredConfig.actions.isEmpty()) {
                    config.actions.addAll(recoveredConfig.actions);
                    loadedCount += recoveredConfig.actions.size();
                } else {
                    config.actions.add(createDamagedAction(actionBlock, e.getMessage(), blockIndex));
                    damagedCount++;
                    LOG.warn("Skipped invalid RunIt action block from " + file.getAbsolutePath(), e);
                }
            }
            blockIndex++;
        }
        return new PartialConfigLoadResult(config, loadedCount, damagedCount);
    }

    private void preserveRawTomlBlock(RunItConfig config, String actionBlock) {
        for (ActionConfig action : config.actions) {
            preserveRawTomlBlock(action, actionBlock);
        }
    }

    private void preserveRawTomlBlock(ActionConfig action, String actionBlock) {
        action.preserveRawTomlBlock = true;
        action.rawTomlBlock = actionBlock;
    }

    private RunItConfig recoverCommandBlock(String actionBlock) {
        RunItConfig recoveredConfig = recoverBasicCommandBlock(actionBlock);
        if (recoveredConfig != null) {
            return recoveredConfig;
        }
        return recoverLegacyLiteralCommandBlock(actionBlock);
    }

    private RunItConfig recoverBasicCommandBlock(String actionBlock) {
        StringBuilder recoveredBlock = new StringBuilder();
        String recoveredCommand = null;
        for (String line : actionBlock.split("\\R", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("command = \"") && trimmed.endsWith("\"")) {
                int quoteStart = line.indexOf('"');
                int quoteEnd = line.lastIndexOf('"');
                if (quoteEnd <= quoteStart) {
                    return null;
                }
                recoveredCommand = decodeTomlBasicString(line.substring(quoteStart + 1, quoteEnd));
                recoveredBlock.append(line, 0, line.indexOf("command = "))
                        .append("command = ''")
                        .append('\n');
            } else {
                recoveredBlock.append(line).append('\n');
            }
        }
        if (recoveredCommand == null) {
            return null;
        }
        RunItConfig recoveredConfig = readConfigFragment(recoveredBlock.toString());
        if (recoveredConfig.actions.isEmpty()) {
            return null;
        }
        for (ActionConfig action : recoveredConfig.actions) {
            action.command = recoveredCommand;
        }
        preserveRawTomlBlock(recoveredConfig, actionBlock);
        return recoveredConfig;
    }

    private String decodeTomlBasicString(String value) {
        StringBuilder decoded = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '\\' || i == value.length() - 1) {
                decoded.append(c);
                continue;
            }

            char next = value.charAt(++i);
            switch (next) {
                case 'b' -> decoded.append('\b');
                case 't' -> decoded.append('\t');
                case 'n' -> decoded.append('\n');
                case 'f' -> decoded.append('\f');
                case 'r' -> decoded.append('\r');
                case '"' -> decoded.append('"');
                case '\\' -> decoded.append('\\');
                case 'u', 'U' -> {
                    int width = next == 'u' ? 4 : 8;
                    if (i + width >= value.length()) {
                        decoded.append('\\').append(next);
                        continue;
                    }
                    String hex = value.substring(i + 1, i + 1 + width);
                    try {
                        decoded.appendCodePoint(Integer.parseInt(hex, 16));
                        i += width;
                    } catch (NumberFormatException e) {
                        decoded.append('\\').append(next);
                    }
                }
                default -> decoded.append('\\').append(next);
            }
        }
        return decoded.toString();
    }

    private RunItConfig recoverLegacyLiteralCommandBlock(String actionBlock) {
        StringBuilder recoveredBlock = new StringBuilder();
        boolean changed = false;
        for (String line : actionBlock.split("\\R", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("command = '''")) {
                String prefix = line.substring(0, line.indexOf("'''"));
                String commandLiteral = line.substring(line.indexOf("'''") + 3);
                if (!commandLiteral.endsWith("'''") || commandLiteral.endsWith("''''")) {
                    return null;
                }
                String command = commandLiteral.substring(0, commandLiteral.length() - 3);
                recoveredBlock.append(prefix).append(TomlStringUtil.quote(command)).append('\n');
                changed = true;
            } else {
                recoveredBlock.append(line).append('\n');
            }
        }
        if (!changed) {
            return null;
        }
        RunItConfig recoveredConfig = readConfigFragment(recoveredBlock.toString());
        preserveRawTomlBlock(recoveredConfig, actionBlock);
        return recoveredConfig;
    }

    private ActionConfig createDamagedAction(String actionBlock, String reason, int blockIndex) {
        ActionConfig action = new ActionConfig(
                RunItBundle.message("dialog.manage.damaged.name", blockIndex),
                "debug",
                reason != null ? reason : RunItBundle.message("dialog.manage.damaged.unknown_reason")
        );
        action.id = "damaged-" + UUID.nameUUIDFromBytes(actionBlock.getBytes(StandardCharsets.UTF_8));
        action.damaged = true;
        action.damagedReason = action.command;
        action.rawTomlBlock = actionBlock;
        return action;
    }

    private RunItConfig readConfigFragment(String actionBlock) {
        Toml toml = new Toml().read("version = 1\n\n" + actionBlock);
        RunItConfig loaded = toml.to(RunItConfig.class);
        if (loaded == null) {
            loaded = new RunItConfig();
        }
        if (loaded.actions == null) {
            loaded.actions = new ArrayList<>();
        }
        return loaded;
    }

    private List<String> extractActionBlocks(String content) {
        List<String> blocks = new ArrayList<>();
        StringBuilder currentBlock = null;
        for (String line : content.split("\\R", -1)) {
            if ("[[actions]]".equals(line.trim())) {
                if (currentBlock != null) {
                    blocks.add(currentBlock.toString());
                }
                currentBlock = new StringBuilder();
            }
            if (currentBlock != null) {
                currentBlock.append(line).append('\n');
            }
        }
        if (currentBlock != null) {
            blocks.add(currentBlock.toString());
        }
        return blocks;
    }

    private void reloadConfig(ActionScope scope) {
        synchronized (configLock) {
            LoadConfigResult result = loadConfigInternal(ActionScope.GLOBAL);
            globalConfig = result.config();
            globalConfigLoadedPartially = result.partial();
            if (actionOrderConfig == null) {
                actionOrderConfig = loadActionOrderConfigInternal();
            }
            normalizeAllActionIds();
            syncActionOrder(false);
        }
    }

    public void reloadAll() {
        synchronized (configLock) {
            LoadConfigResult result = loadConfigInternal(ActionScope.GLOBAL);
            globalConfig = result.config();
            globalConfigLoadedPartially = result.partial();
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
                    backupPartiallyLoadedConfig(scope, file);
                    java.nio.file.Files.write(file.toPath(), config.toToml().getBytes(StandardCharsets.UTF_8));
                    if (scope == ActionScope.GLOBAL) {
                        globalConfigLoadedPartially = hasDamagedActions(config);
                    }
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

    private void backupPartiallyLoadedConfig(ActionScope scope, File file) throws IOException {
        if (scope != ActionScope.GLOBAL || !globalConfigLoadedPartially || !file.exists()) {
            return;
        }
        java.nio.file.Files.copy(file.toPath(), new File(file.getAbsolutePath() + ".bak").toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private boolean hasDamagedActions(RunItConfig config) {
        for (ActionConfig action : config.actions) {
            if (action != null && action.isDamaged()) {
                return true;
            }
        }
        return false;
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
        List<ScopedAction> currentActions = buildFilteredActions(ActionListFilter.ALL);
        List<String> resolvedOrder = resolveActionOrder(currentActions);
        List<String> currentOrder = actionOrderConfig.getActionOrder(getProjectOrderKey());
        if (!Objects.equals(resolvedOrder, currentOrder)) {
            actionOrderConfig.setActionOrder(getProjectOrderKey(), resolvedOrder);
            if (saveIfChanged || (!globalConfigLoadedPartially && hasUnknownActionIds(currentOrder, currentActions))) {
                saveActionOrder(resolvedOrder);
            }
        }
    }

    private boolean hasUnknownActionIds(List<String> actionIds, List<ScopedAction> currentActions) {
        Set<String> existingIds = new LinkedHashSet<>();
        for (ScopedAction action : currentActions) {
            existingIds.add(action.getActionConfig().id);
        }
        for (String actionId : actionIds) {
            if (!existingIds.contains(actionId)) {
                return true;
            }
        }
        return false;
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
        if (globalDirty && !globalConfigLoadedPartially) {
            saveConfig(ActionScope.GLOBAL);
        }
        return globalDirty;
    }

    private boolean normalizeActions(RunItConfig config, Set<String> usedIds) {
        boolean changed = false;
        for (int i = 0; i < config.actions.size(); i++) {
            ActionConfig action = config.actions.get(i);
            if (action == null) {
                config.actions.remove(i);
                i--;
                changed = true;
                continue;
            }

            if (action.name == null) {
                action.name = "";
                changed = true;
            }
            if (trimToNull(action.icon) == null) {
                action.icon = "run";
                changed = true;
            }
            if (action.command == null) {
                action.command = "";
                changed = true;
            }

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

    private record LoadConfigResult(RunItConfig config, boolean partial) {
    }

    private record PartialConfigLoadResult(RunItConfig config, int loadedCount, int skippedCount) {
    }
}
