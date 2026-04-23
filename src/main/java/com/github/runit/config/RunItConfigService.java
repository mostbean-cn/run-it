package com.github.runit.config;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class RunItConfigService implements Disposable {
    private final Project project;
    private volatile RunItConfig config;
    private final Object configLock = new Object();
    private final AtomicBoolean loading = new AtomicBoolean(false);

    public RunItConfigService(Project project) {
        this.project = project;
        setupFileWatcher();
        // Pre-load config asynchronously to avoid blocking EDT
        ApplicationManager.getApplication().executeOnPooledThread(this::ensureConfigLoaded);
    }

    public static RunItConfigService getInstance(Project project) {
        return project.getService(RunItConfigService.class);
    }

    private File getConfigFile() {
        return new File(project.getBasePath(), ".runit/runit.toml");
    }

    private File getConfigDir() {
        return new File(project.getBasePath(), ".runit");
    }

    private void ensureConfigLoaded() {
        if (config != null) return;
        synchronized (configLock) {
            if (config != null) return;
            loadConfigInternal();
        }
    }

    private void loadConfigInternal() {
        File file = getConfigFile();
        if (file.exists()) {
            try {
                Toml toml = new Toml().read(file);
                config = toml.to(RunItConfig.class);
                if (config.actions == null) {
                    config.actions = new java.util.ArrayList<>();
                }
            } catch (Exception e) {
                config = new RunItConfig();
            }
        } else {
            config = new RunItConfig();
        }
    }

    private void loadConfig() {
        ensureConfigLoaded();
    }

    public void saveConfig() {
        ApplicationManager.getApplication().invokeLater(() -> {
            WriteAction.run(() -> {
                try {
                    File dir = getConfigDir();
                    if (!dir.exists()) {
                        dir.mkdirs();
                    }
                    File file = getConfigFile();
                    java.nio.file.Files.write(file.toPath(), config.toToml().getBytes(StandardCharsets.UTF_8));
                    VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
                    if (virtualFile != null) {
                        virtualFile.refresh(false, false);
                    }
                } catch (IOException e) {
                    com.intellij.openapi.diagnostic.Logger.getInstance(RunItConfigService.class)
                            .error("Failed to save RunIt config", e);
                }
            });
        });
    }

    public RunItConfig getConfig() {
        ensureConfigLoaded();
        return config;
    }

    /**
     * Non-blocking check to get config if already loaded.
     * Returns null if config hasn't been loaded yet.
     */
    public RunItConfig getConfigIfLoaded() {
        return config;
    }

    public void addAction(ActionConfig action) {
        ensureConfigLoaded();
        config.actions.add(action);
        saveConfig();
    }

    public void updateAction(int index, ActionConfig action) {
        ensureConfigLoaded();
        if (index >= 0 && index < config.actions.size()) {
            config.actions.set(index, action);
            saveConfig();
        }
    }

    public void removeAction(int index) {
        ensureConfigLoaded();
        if (index >= 0 && index < config.actions.size()) {
            config.actions.remove(index);
            saveConfig();
        }
    }

    public void moveAction(int fromIndex, int toIndex) {
        ensureConfigLoaded();
        int actionCount = config.actions.size();
        if (fromIndex < 0 || fromIndex >= actionCount || toIndex < 0 || toIndex >= actionCount || fromIndex == toIndex) {
            return;
        }

        ActionConfig action = config.actions.remove(fromIndex);
        config.actions.add(toIndex, action);
        saveConfig();
    }

    private void setupFileWatcher() {
        project.getMessageBus().connect(this).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
            @Override
            public void after(@NotNull List<? extends VFileEvent> events) {
                File configFile = getConfigFile();
                for (VFileEvent event : events) {
                    String path = event.getPath();
                    if (path != null && path.equals(configFile.getAbsolutePath().replace('\\', '/'))) {
                        loadConfig();
                        break;
                    }
                }
            }
        });
    }

    @Override
    public void dispose() {
    }
}
