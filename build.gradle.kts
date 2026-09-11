import java.io.File

plugins {
    // Подключает стандартную поддержку Java-приложения и плагин для JavaFX.
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

// Maven Central используется как источник зависимостей Gradle.
repositories {
    mavenCentral()
}

java {
    toolchain {
        // Версия JDK задаётся явно, чтобы сборка не зависела от JDK по умолчанию на компьютере.
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

javafx {
    // Версия JavaFX должна соответствовать используемой версии JDK.
    version = "25.0.4"

    // Нужны элементы управления и загрузка интерфейса из FXML.
    modules("javafx.controls", "javafx.fxml")
}

application {
    // Gradle использует этот класс как точку входа при запуске приложения.
    mainClass.set("structura.StructuraApp")
}

val packageInputDirectory = layout.buildDirectory.dir("jpackage/input")
val packageOutputDirectory = layout.buildDirectory.dir("package")

val preparePackageInput = tasks.register<Sync>("preparePackageInput") {
    dependsOn(tasks.jar)
    from(tasks.jar)
    from(configurations.runtimeClasspath)
    into(packageInputDirectory)
}

tasks.register<Exec>("packageApp") {
    group = "distribution"
    description = "Создаёт автономное desktop-приложение в build/package"
    dependsOn(preparePackageInput)

    val appDirectory = packageOutputDirectory.map { it.dir("Structura") }
    inputs.dir(packageInputDirectory)
    outputs.dir(appDirectory)

    doFirst {
        delete(appDirectory)

        val jpackage = File(System.getProperty("java.home"), "bin/jpackage")
        require(jpackage.exists()) {
            "jpackage не найден. Запустите Gradle через полный JDK 25, а не JRE."
        }

        executable = jpackage.absolutePath
        args(
            "--type", "app-image",
            "--name", "Structura",
            "--description", "Конструктор структуры проекта",
            "--vendor", "Structura",
            "--app-version", "1.0.0",
            "--input", packageInputDirectory.get().asFile.absolutePath,
            "--dest", packageOutputDirectory.get().asFile.absolutePath,
            "--main-jar", tasks.jar.get().archiveFileName.get(),
            "--main-class", "structura.Launcher",
            "--java-options", "--enable-native-access=ALL-UNNAMED"
        )
    }
}
