package structura.model;

import java.util.ArrayList;
import java.util.List;
import java.nio.file.Path;

public class FileNode {
    public enum NodeType {FOLDER, FILE}

    private String name;
    private final NodeType type;
    private final List<FileNode> children;
    private Path sourcePath;

    public FileNode(String name, NodeType type) {
        this.name = name;
        this.type = type;
        this.children = new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /** Исходный файл на диске; null у элементов, созданных только в редакторе. */
    public Path getSourcePath() {
        return sourcePath;
    }

    public void setSourcePath(Path sourcePath) {
        this.sourcePath = sourcePath;
    }

    public NodeType getType() {
        return type;
    }

    public List<FileNode> getChildren() {
        return children;
    }

    public boolean isFolder() {
        return type == NodeType.FOLDER;
    }

    public void addChild(FileNode child) {
        if (!isFolder()) {
            throw new IllegalStateException("Нельзя добавить дочерний узел в файл");
        }
        children.add(child);
    }

    public void removeChild(FileNode child) {
        children.remove(child);
    }

    @Override
    public String toString() {
        return (isFolder() ? "📁 " : "📄 ") + name;
    }
}
