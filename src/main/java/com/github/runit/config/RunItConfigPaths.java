package com.github.runit.config;

import com.intellij.openapi.project.Project;

import java.io.File;
import java.util.Locale;

public final class RunItConfigPaths {
    private static final String PROJECT_DIR_NAME = ".runit";
    private static final String GLOBAL_DIR_NAME = "run-it";
    private static final String CONFIG_FILE_NAME = "runit.toml";
    private static final String ORDER_FILE_NAME = "action-order.toml";

    private RunItConfigPaths() {
    }

    public static File getConfigFile(Project project, ActionScope scope) {
        return scope == ActionScope.GLOBAL ? getGlobalConfigFile() : getProjectConfigFile(project);
    }

    public static String getDisplayPath(Project project, ActionScope scope) {
        return getConfigFile(project, scope).getAbsolutePath();
    }

    public static File getActionOrderFile() {
        return new File(getGlobalConfigDir(), ORDER_FILE_NAME);
    }

    private static File getProjectConfigFile(Project project) {
        String basePath = project.getBasePath();
        return basePath != null
                ? new File(new File(basePath, PROJECT_DIR_NAME), CONFIG_FILE_NAME)
                : new File(new File(PROJECT_DIR_NAME), CONFIG_FILE_NAME).getAbsoluteFile();
    }

    private static File getGlobalConfigFile() {
        return new File(getGlobalConfigDir(), CONFIG_FILE_NAME);
    }

    private static File getGlobalConfigDir() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            return new File(getWindowsConfigRoot(), GLOBAL_DIR_NAME);
        }
        if (osName.contains("mac")) {
            return new File(new File(getUserHome(), "Library/Application Support"), GLOBAL_DIR_NAME);
        }
        return new File(getLinuxConfigRoot(), GLOBAL_DIR_NAME);
    }

    private static File getWindowsConfigRoot() {
        String appData = trimToNull(System.getenv("APPDATA"));
        return appData != null ? new File(appData) : new File(getUserHome(), "AppData/Roaming");
    }

    private static File getLinuxConfigRoot() {
        String xdgConfigHome = trimToNull(System.getenv("XDG_CONFIG_HOME"));
        return xdgConfigHome != null ? new File(xdgConfigHome) : new File(getUserHome(), ".config");
    }

    private static String getUserHome() {
        String userHome = trimToNull(System.getProperty("user.home"));
        return userHome != null ? userHome : new File(".").getAbsoluteFile().getPath();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
