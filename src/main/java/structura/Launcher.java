package structura;

/**
 * Обычная точка входа для нативного launcher-а, создаваемого jpackage.
 * Отдельный класс позволяет корректно запускать JavaFX-приложение с classpath.
 */
public final class Launcher {
    private Launcher() {
    }

    public static void main(String[] args) {
        StructuraApp.main(args);
    }
}
