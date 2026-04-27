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
        if (executeInTerminalToolWindow(project, title, command)) {
            return true;
        }

        return executeInReworkedTerminal(project, title, command);
    }

    private static boolean executeInTerminalToolWindow(Project project, String title, String command) {
        try {
            Object terminalManager = getTerminalManager(project);
            if (terminalManager == null) {
                return false;
            }

            String workingDirectory = project.getBasePath();
            Object widget = createShellWidget(terminalManager, workingDirectory, title);
            if (widget != null && executeTerminalCommand(widget, command)) {
                activateTerminal(project);
                return true;
            }

            Object legacyWidget = createLegacyShellWidget(terminalManager, workingDirectory, title);
            if (legacyWidget != null && executeTerminalCommand(legacyWidget, command)) {
                activateTerminal(project);
                return true;
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Terminal APIs moved across IDE versions. Try the reworked terminal path next.
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
            if (!sendCommandToReworkedTerminal(view, command)) {
                return false;
            }
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

    private static Object invokeIfAvailable(Object target, String methodName, boolean argument)
            throws ReflectiveOperationException {
        Method method = findMethod(target.getClass(), methodName, boolean.class);
        if (method == null) {
            return null;
        }
        return invoke(method, target, argument);
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

    private static boolean executeTerminalCommand(Object widget, String command) {
        if (sendCommandToTerminal(widget, command)) {
            return true;
        }
        if (executeLegacyTerminalCommand(widget, command)) {
            return true;
        }
        Object shellWidget = unwrapShellTerminalWidget(widget);
        return shellWidget != null && executeLegacyTerminalCommand(shellWidget, command);
    }

    private static boolean sendCommandToTerminal(Object widget, String command) {
        Method sendCommand = findTerminalWidgetMethod(widget, "sendCommandToExecute");
        if (sendCommand == null) {
            return false;
        }
        return invokeCommandMethod(sendCommand, widget, command);
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

    private static boolean executeLegacyTerminalCommand(Object widget, String command) {
        Method executeCommand = findMethod(widget.getClass(), "executeCommand", String.class);
        if (executeCommand == null) {
            return false;
        }
        return invokeCommandMethod(executeCommand, widget, command);
    }

    private static boolean sendCommandToReworkedTerminal(Object view, String command) throws ReflectiveOperationException {
        Method createSendTextBuilder = findMethod(view.getClass(), "createSendTextBuilder");
        if (createSendTextBuilder == null) {
            return false;
        }

        Object sendTextBuilder = invoke(createSendTextBuilder, view);
        Object executableTextBuilder = invokeIfAvailable(sendTextBuilder, "shouldExecute", true);
        if (executableTextBuilder == null) {
            executableTextBuilder = invokeIfAvailable(sendTextBuilder, "shouldExecute");
        }
        if (executableTextBuilder == null) {
            executableTextBuilder = sendTextBuilder;
        }
        return invokeCommandMethod(executableTextBuilder, command, "sendText", "send");
    }

    private static Object unwrapShellTerminalWidget(Object widget) {
        try {
            Class<?> shellWidgetClass = Class.forName("org.jetbrains.plugins.terminal.ShellTerminalWidget");
            if (shellWidgetClass.isInstance(widget)) {
                return widget;
            }

            Class<?> terminalWidgetClass = Class.forName("com.intellij.terminal.ui.TerminalWidget");
            if (!terminalWidgetClass.isInstance(widget)) {
                return null;
            }

            Method asShellWidget = findMethod(shellWidgetClass, "asShellJediTermWidget", terminalWidgetClass);
            if (asShellWidget == null) {
                asShellWidget = findMethod(shellWidgetClass, "toShellJediTermWidgetOrThrow", terminalWidgetClass);
            }
            if (asShellWidget == null) {
                return null;
            }
            return invoke(asShellWidget, null, widget);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static boolean invokeCommandMethod(Object target, String command, String... methodNames) {
        for (String methodName : methodNames) {
            Method method = findMethod(target.getClass(), methodName, String.class);
            if (method != null && invokeCommandMethod(method, target, command)) {
                return true;
            }
        }
        return false;
    }

    private static boolean invokeCommandMethod(Method method, Object target, String command) {
        try {
            invoke(method, target, command);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static Object invokeIfAvailable(Object target, String methodName)
            throws ReflectiveOperationException {
        Method method = findMethod(target.getClass(), methodName);
        if (method == null) {
            return null;
        }
        return invoke(method, target);
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getMethod(name, parameterTypes);
            } catch (NoSuchMethodException ignored) {
            }

            try {
                return current.getDeclaredMethod(name, parameterTypes);
            } catch (NoSuchMethodException ignored) {
            }

            Method interfaceMethod = findInterfaceMethod(current, name, parameterTypes);
            if (interfaceMethod != null) {
                return interfaceMethod;
            }

            current = current.getSuperclass();
        }
        return null;
    }

    private static Method findTerminalWidgetMethod(Object widget, String name) {
        try {
            Class<?> terminalWidgetClass = Class.forName("com.intellij.terminal.ui.TerminalWidget");
            if (terminalWidgetClass.isInstance(widget)) {
                return terminalWidgetClass.getMethod(name, String.class);
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return findMethod(widget.getClass(), name, String.class);
    }

    private static Method findInterfaceMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        for (Class<?> interfaceType : type.getInterfaces()) {
            try {
                return interfaceType.getMethod(name, parameterTypes);
            } catch (NoSuchMethodException ignored) {
            }

            Method method = findInterfaceMethod(interfaceType, name, parameterTypes);
            if (method != null) {
                return method;
            }
        }
        return null;
    }

    private static Object invoke(Method method, Object target, Object... args) throws ReflectiveOperationException {
        try {
            try {
                if (!method.canAccess(target)) {
                    method.setAccessible(true);
                }
            } catch (RuntimeException ignored) {
            }
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
