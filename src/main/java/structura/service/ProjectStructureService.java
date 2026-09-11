package structura.service;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

import structura.model.FileNode;
import structura.model.FileNode.NodeType;

public class ProjectStructureService {
    public enum ProgrammingLanguage {
        JAVA("Java", ".java"),
        KOTLIN("Kotlin", ".kt"),
        PYTHON("Python", ".py"),
        JAVASCRIPT("JavaScript", ".js"),
        TYPESCRIPT("TypeScript", ".ts"),
        GO("Go", ".go"),
        CSHARP("C#", ".cs"),
        CPP("C++", ".cpp");

        private final String title;
        private final String extension;

        ProgrammingLanguage(String title, String extension) {
            this.title = title;
            this.extension = extension;
        }

        public String getExtension() {
            return extension;
        }

        @Override
        public String toString() {
            return title + " (" + extension + ")";
        }
    }

    public enum ProjectTemplate {
        SPRING_BOOT("Spring Boot"),
        EMPTY("Пустой проект");

        private final String title;

        ProjectTemplate(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    public FileNode createProjectTree(String projectName, ProjectTemplate template) {
        FileNode project = new FileNode(projectName, NodeType.FOLDER);
        if (template == ProjectTemplate.SPRING_BOOT) {
            addSpringBootStructure(project);
        }
        return project;
    }

    private void addSpringBootStructure(FileNode project) {
        FileNode src = new FileNode("src", NodeType.FOLDER);
        FileNode main = new FileNode("main", NodeType.FOLDER);
        main.addChild(new FileNode("java", NodeType.FOLDER));
        main.addChild(new FileNode("resources", NodeType.FOLDER));

        FileNode test = new FileNode("test", NodeType.FOLDER);
        test.addChild(new FileNode("java", NodeType.FOLDER));

        src.addChild(main);
        src.addChild(test);
        project.addChild(src);
        project.addChild(new FileNode("build.gradle.kts", NodeType.FILE));
        project.addChild(new FileNode("settings.gradle.kts", NodeType.FILE));
    }

    public void saveTree(FileNode root, Path destination) throws IOException {
        Path projectPath = destination.resolve(root.getName());
        if (Files.exists(projectPath)) {
            throw new FileAlreadyExistsException(projectPath.toString());
        }
        saveNode(root, destination);
    }

    private void saveNode(FileNode node, Path parentPath) throws IOException {
        Path currentPath = parentPath.resolve(node.getName());

        if (node.isFolder()) {
            Files.createDirectories(currentPath);
            for (FileNode child : node.getChildren()) {
                saveNode(child, currentPath);
            }
        } else {
            if (node.getSourcePath() != null && Files.exists(node.getSourcePath(), LinkOption.NOFOLLOW_LINKS)) {
                Files.copy(node.getSourcePath(), currentPath, LinkOption.NOFOLLOW_LINKS,
                        StandardCopyOption.COPY_ATTRIBUTES);
            } else {
                Files.createFile(currentPath);
            }
        }
    }

    /** Читает существующую папку, не переходя по символическим ссылкам. */
    public FileNode loadTree(Path directory) throws IOException {
        Path root = directory.toAbsolutePath().normalize();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Выбранный путь не является папкой");
        }
        return loadNode(root);
    }

    private FileNode loadNode(Path path) throws IOException {
        boolean folder = Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS);
        Path fileName = path.getFileName();
        FileNode node = new FileNode(fileName == null ? path.toString() : fileName.toString(),
                folder ? NodeType.FOLDER : NodeType.FILE);
        node.setSourcePath(path.toAbsolutePath().normalize());
        if (folder) {
            try (Stream<Path> entries = Files.list(path)) {
                for (Path child : entries.sorted(Comparator
                        .comparing((Path child) -> !Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS))
                        .thenComparing(child -> child.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                        .toList()) {
                    node.addChild(loadNode(child));
                }
            }
        }
        return node;
    }

    /**
     * Применяет дерево к открытой папке через соседнюю временную копию. Исходная
     * папка заменяется только после того, как новое дерево полностью подготовлено.
     */
    public Path updateExistingTree(FileNode root, Path openedDirectory) throws IOException {
        Path original = openedDirectory.toAbsolutePath().normalize();
        Path parent = original.getParent();
        if (parent == null) throw new IOException("Корневую папку файловой системы изменить нельзя");

        Path desired = parent.resolve(root.getName()).normalize();
        if (!desired.equals(original) && Files.exists(desired, LinkOption.NOFOLLOW_LINKS)) {
            throw new FileAlreadyExistsException(desired.toString());
        }

        Path prepared = Files.createTempDirectory(parent, ".structura-next-");
        Path backup = parent.resolve(".structura-backup-" + UUID.randomUUID());
        boolean originalMoved = false;
        try {
            materializeChildren(root, prepared);
            move(original, backup);
            originalMoved = true;
            try {
                move(prepared, desired);
            } catch (IOException installError) {
                move(backup, original);
                originalMoved = false;
                throw installError;
            }
            originalMoved = false;
            updateSourcePaths(root, desired);
            try {
                deleteRecursively(backup);
            } catch (IOException ignored) {
                // Сохранённая структура уже установлена; резервная папка безопаснее удаления данных.
            }
            return desired;
        } finally {
            if (Files.exists(prepared, LinkOption.NOFOLLOW_LINKS)) deleteRecursively(prepared);
            if (originalMoved && Files.exists(backup, LinkOption.NOFOLLOW_LINKS)
                    && !Files.exists(original, LinkOption.NOFOLLOW_LINKS)) {
                move(backup, original);
            }
        }
    }

    private void materializeChildren(FileNode parentNode, Path parentPath) throws IOException {
        for (FileNode child : parentNode.getChildren()) {
            Path target = parentPath.resolve(child.getName());
            if (child.isFolder()) {
                Files.createDirectory(target);
                materializeChildren(child, target);
            } else if (child.getSourcePath() != null
                    && Files.exists(child.getSourcePath(), LinkOption.NOFOLLOW_LINKS)) {
                Files.copy(child.getSourcePath(), target, LinkOption.NOFOLLOW_LINKS,
                        StandardCopyOption.COPY_ATTRIBUTES);
            } else {
                Files.createFile(target);
            }
        }
    }

    private void updateSourcePaths(FileNode node, Path path) {
        node.setSourcePath(path);
        for (FileNode child : node.getChildren()) {
            updateSourcePaths(child, path.resolve(child.getName()));
        }
    }

    private void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target);
        }
    }

    private void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}
