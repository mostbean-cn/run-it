package com.github.runit.executor;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

final class TerminalCommandRunner {
    private static final String REWORKED_TERMINAL_MANAGER_CLASS =
            "com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager";
    private static final String TERMINAL_MANAGER_CLASS = "org.jetbrains.plugins.terminal.TerminalToolWindowManager";
    private static final String TERMINAL_TOOL_WINDOW_ID = "Terminal";

    private TerminalCommandRunner() {
    }

    static boolean execute(Project project, String title, String command) {
        if (executeInReworkedTerminal(project, title, command)) {
            return true;
        }

        try {
            Object terminalManager = getTerminalManager(project);
            if (terminalManager == null) {
                return false;
            }

            String workingDirectory = project.getBasePath();
            Object widget = createShellWidget(terminalManager, workingDirectory, title);
            if (widget != null && sendCommandToTerminal(widget, command)) {
                activateTerminal(project);
                return true;
            }

            Object legacyWidget = createLegacyShellWidget(terminalManager, workingDirectory, title);
            if (legacyWidget != null && executeLegacyTerminalCommand(legacyWidget, command)) {
                activateTerminal(project);
                return true;
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Terminal APIs moved across IDE versions. Fall back to the stable Run tool window.
        }
        return false;
    }

    private static boolean executeInReworkedTerminal(Project project, String title, String command) {
        try {
            Class<?> managerClass = Class.forName(REWORKED_TERMINAL_MANAGER_CLASS);
            Object terminalManager = getProjectService(project, managerClass);
            if (terminalManager == null) {
                return false;
            }

            Object tabBuilder = invoke(managerClass.getMethod("createTabBuilder"), terminalManager);
            invokeIfAvailable(tabBuilder, "workingDirectory", project.getBasePath());
            invokeIfAvailable(tabBuilder, "tabName", title);
            invokeIfAvailable(tabBuilder, "requestFocus", true);
            invokeIfAvailable(tabBuilder, "deferSessionStartUntilUiShown", false);

            Object tab = invoke(tabBuilder.getClass().getMethod("createTab"), tabBuilder);
            Object view = invoke(tab.getClass().getMethod("getView"), tab);
            Object sendTextBuilder = invoke(view.getClass().getMethod("createSendTextBuilder"), view);
            Object executableTextBuilder = invoke(sendTextBuilder.getClass().getMethod("shouldExecute"), sendTextBuilder);
            invoke(executableTextBuilder.getClass().getMethod("send", String.class), executableTextBuilder, command);
            activateTerminal(project);
            return true;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    private static Object getTerminalManager(Project project) throws ReflectiveOperationException {
        Class<?> managerClass = Class.forName(TERMINAL_MANAGER_CLASS);
        return getProjectService(project, managerClass);
    }

    private static Object getProjectService(Project project, Class<?> managerClass) throws ReflectiveOperationException {
        Object service = project.getService(managerClass);
        if (service != null) {
            return service;
        }
        Method getInstance = managerClass.getMethod("getInstance", Project.class);
        return getInstance.invoke(null, project);
    }

    private static void invokeIfAvailable(Object target, String methodName, String argument)
            throws ReflectiveOperationException {
        Method method = findMethod(target.getClass(), methodName, String.class);
        if (method != null) {
            invoke(method, target, argument);
        }
    }

    private static void invokeIfAvailable(Object target, String methodName, boolean argument)
            throws ReflectiveOperationException {
        Method method = findMethod(target.getClass(), methodName, boolean.class);
        if (method != null) {
            invoke(method, target, argument);
        }
    }

    private static Object createShellWidget(Object terminalManager, String workingDirectory, String title)
            throws ReflectiveOperationException {
        Method createShellWidget = findMethod(
                terminalManager.getClass(),
                "createShellWidget",
                String.class,
                String.class,
                boolean.class,
                boolean.class
        );
        if (createShellWidget == null) {
            return null;
        }
        return invoke(createShellWidget, terminalManager, workingDirectory, title, true, true);
    }

    private static boolean sendCommandToTerminal(Object widget, String command) throws ReflectiveOperationException {
        Method sendCommand = findMethod(widget.getClass(), "sendCommandToExecute", String.class);
        if (sendCommand == null) {
            return false;
        }
        invoke(sendCommand, widget, command);
        return true;
    }

    private static Object createLegacyShellWidget(Object terminalManager, String workingDirectory, String title)
            throws ReflectiveOperationException {
        Method createLocalShellWidget = findMethod(
                terminalManager.getClass(),
                "createLocalShellWidget",
                String.class,
                String.class
        );
        if (createLocalShellWidget == null) {
            return null;
        }
        return invoke(createLocalShellWidget, terminalManager, workingDirectory, title);
    }

    private static boolean executeLegacyTerminalCommand(Object widget, String command) throws ReflectiveOperationException {
        Method executeCommand = findMethod(widget.getClass(), "executeCommand", String.class);
        if (executeCommand == null) {
            return false;
        }
        invoke(executeCommand, widget, command);
        return true;
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getMethod(name, parameterTypes);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static Object invoke(Method method, Object target, Object... args) throws ReflectiveOperationException {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ReflectiveOperationException reflectiveOperationException) {
                throw reflectiveOperationException;
            }
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw e;
        }
    }

    private static void activateTerminal(Project project) {
        ToolWindow terminalToolWindow = ToolWindowManager.getInstance(project).getToolWindow(TERMINAL_TOOL_WINDOW_ID);
        if (terminalToolWindow != null) {
            terminalToolWindow.activate(null);
        }
    }
}
