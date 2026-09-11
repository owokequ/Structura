package structura.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.DirectoryChooser;
import structura.model.FileNode;
import structura.service.ProjectStructureService;

public class MainController {
    @FXML private Label statusLabel;
    @FXML private ComboBox<ProjectStructureService.ProjectTemplate> templateBox;
    @FXML private ComboBox<ProjectStructureService.ProgrammingLanguage> languageBox;
    @FXML private TextField projectNameField;
    @FXML private TextField locationField;
    @FXML private TreeView<FileNode> structureTree;

    private final ProjectStructureService projectStructureService = new ProjectStructureService();
    private Path baseDir;
    private Path openedDirectory;
    private TreeItem<FileNode> draggedItem;
    private EditableTreeCell draggedCell;

    @FXML
    private void initialize() {
        templateBox.getItems().setAll(ProjectStructureService.ProjectTemplate.values());
        templateBox.setValue(ProjectStructureService.ProjectTemplate.SPRING_BOOT);
        languageBox.getItems().setAll(ProjectStructureService.ProgrammingLanguage.values());
        languageBox.setValue(ProjectStructureService.ProgrammingLanguage.JAVA);
        structureTree.setEditable(true);
        structureTree.setCellFactory(tree -> new EditableTreeCell(this));
        structureTree.setOnKeyPressed(this::handleTreeKeys);
        Platform.runLater(() -> structureTree.getScene()
                .addEventHandler(KeyEvent.KEY_PRESSED, this::handleWindowKeys));
        statusLabel.setText("Готово");
    }

    @FXML
    private void selectDir() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Выберите папку");
        File selectedFolder = chooser.showDialog(locationField.getScene().getWindow());
        if (selectedFolder != null) {
            baseDir = selectedFolder.toPath();
            openedDirectory = null;
            locationField.setText(baseDir.toString());
            statusLabel.setText("Папка назначения выбрана");
        }
    }

    @FXML
    private void openExistingDirectory() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Открыть существующую папку");
        File selectedFolder = chooser.showDialog(locationField.getScene().getWindow());
        if (selectedFolder == null) return;
        try {
            Path selectedPath = selectedFolder.toPath().toAbsolutePath().normalize();
            FileNode folderTree = projectStructureService.loadTree(selectedPath);
            structureTree.setRoot(toTreeItem(folderTree));
            structureTree.getSelectionModel().select(structureTree.getRoot());
            structureTree.requestFocus();
            openedDirectory = selectedPath;
            baseDir = selectedPath.getParent();
            locationField.setText(selectedPath.toString());
            projectNameField.setText(folderTree.getName());
            statusLabel.setText("Папка открыта — изменения ещё не записаны на диск");
        } catch (IOException exception) {
            statusLabel.setText("Не удалось открыть папку: " + exception.getMessage());
        }
    }

    @FXML
    private void createProject() {
        String projectName = projectNameField.getText().trim();
        if (!isValidName(projectName, "проекта")) return;
        FileNode projectTree = projectStructureService.createProjectTree(projectName, templateBox.getValue());
        openedDirectory = null;
        structureTree.setRoot(toTreeItem(projectTree));
        structureTree.getSelectionModel().select(structureTree.getRoot());
        structureTree.requestFocus();
        statusLabel.setText("Структура готова — добавляйте папки и файлы");
    }

    @FXML private void addFolder() { addNode(FileNode.NodeType.FOLDER); }
    @FXML private void addFile() { addNode(FileNode.NodeType.FILE); }

    private void addNode(FileNode.NodeType type) {
        TreeItem<FileNode> selected = structureTree.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Сначала создайте структуру проекта");
            return;
        }
        TreeItem<FileNode> parent = selected.getValue().isFolder() ? selected : selected.getParent();
        if (parent == null) {
            statusLabel.setText("Не удалось определить папку для нового элемента");
            return;
        }
        String name = nextNewName(parent.getValue(), type);
        FileNode child = new FileNode(name, type);
        parent.getValue().addChild(child);
        TreeItem<FileNode> childItem = toTreeItem(child);
        parent.getChildren().add(childItem);
        parent.setExpanded(true);
        structureTree.getSelectionModel().select(childItem);
        structureTree.scrollTo(structureTree.getRow(childItem));
        statusLabel.setText((type == FileNode.NodeType.FOLDER ? "Папка" : "Файл") + " создан — введите имя");
        Platform.runLater(() -> structureTree.edit(childItem));
    }

    boolean rename(TreeItem<FileNode> item, String enteredName) {
        FileNode node = item.getValue();
        String newName = enteredName.trim();
        if (!isValidName(newName, node.isFolder() ? "папки" : "файла")) return false;
        TreeItem<FileNode> parent = item.getParent();
        if (parent != null && parent.getValue().getChildren().stream()
                .anyMatch(child -> child != node && child.getName().equals(newName))) {
            statusLabel.setText("Элемент с таким именем уже есть в этой папке");
            return false;
        }
        if (newName.equals(node.getName())) {
            statusLabel.setText("Имя не изменилось");
            return true;
        }
        node.setName(newName);
        if (item == structureTree.getRoot()) projectNameField.setText(newName);
        statusLabel.setText("Элемент переименован");
        return true;
    }

    @FXML
    private void deleteSelected() {
        TreeItem<FileNode> selected = structureTree.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Выберите файл или папку для удаления");
            return;
        }
        if (selected == structureTree.getRoot()) {
            statusLabel.setText("Корневую папку проекта удалить нельзя");
            return;
        }
        TreeItem<FileNode> parent = selected.getParent();
        parent.getValue().removeChild(selected.getValue());
        parent.getChildren().remove(selected);
        structureTree.getSelectionModel().select(parent);
        statusLabel.setText("Элемент удалён из структуры");
    }

    @FXML
    private void saveProject() {
        TreeItem<FileNode> root = structureTree.getRoot();
        if (root == null) {
            statusLabel.setText("Сначала создайте структуру проекта");
            return;
        }
        if (baseDir == null && openedDirectory == null) {
            statusLabel.setText("Выберите папку для проекта");
            return;
        }
        try {
            if (openedDirectory != null) {
                if (!confirmExistingDirectoryUpdate()) return;
                openedDirectory = projectStructureService.updateExistingTree(root.getValue(), openedDirectory);
                baseDir = openedDirectory.getParent();
                locationField.setText(openedDirectory.toString());
                statusLabel.setText("Изменения сохранены: " + openedDirectory);
            } else {
                projectStructureService.saveTree(root.getValue(), baseDir);
                statusLabel.setText("Проект сохранён: " + baseDir.resolve(root.getValue().getName()));
            }
        } catch (IOException exception) {
            statusLabel.setText("Не удалось сохранить проект: " + exception.getMessage());
        }
    }

    private boolean confirmExistingDirectoryUpdate() {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.initOwner(structureTree.getScene().getWindow());
        confirmation.setTitle("Сохранить изменения");
        confirmation.setHeaderText("Применить дерево к открытой папке?");
        confirmation.setContentText("Переименованные и перемещённые элементы будут изменены на диске, "
                + "а удалённые из дерева — удалены из папки.");
        confirmation.getDialogPane().getStylesheets().add(MainController.class
                .getResource("/structura/view/main.css").toExternalForm());
        confirmation.getDialogPane().getStyleClass().add("confirmation-dialog");
        return confirmation.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    private String nextNewName(FileNode parent, FileNode.NodeType type) {
        String base = type == FileNode.NodeType.FOLDER ? "NewFolder" : "NewFile";
        String extension = type == FileNode.NodeType.FILE ? languageBox.getValue().getExtension() : "";
        int number = 1;
        String candidate = base + extension;
        while (hasChildWithName(parent, candidate)) {
            candidate = base + (++number) + extension;
        }
        return candidate;
    }

    private boolean isValidName(String name, String target) {
        if (name.isBlank()) {
            statusLabel.setText("Название " + target + " не может быть пустым");
            return false;
        }
        if (name.contains("/") || name.contains("\\")) {
            statusLabel.setText("Название " + target + " не должно содержать слеши");
            return false;
        }
        return true;
    }

    private boolean hasChildWithName(FileNode parent, String name) {
        return parent.getChildren().stream().anyMatch(child -> child.getName().equals(name));
    }

    private void handleTreeKeys(KeyEvent event) {
        if (event.getCode() == KeyCode.DELETE) {
            deleteSelected();
            event.consume();
        } else if (event.isAltDown() && event.getCode() == KeyCode.P) {
            addFolder();
            event.consume();
        } else if (event.isAltDown() && event.getCode() == KeyCode.F) {
            addFile();
            event.consume();
        } else if (event.getCode() == KeyCode.F2) {
            TreeItem<FileNode> selected = structureTree.getSelectionModel().getSelectedItem();
            if (selected != null) structureTree.edit(selected);
            else statusLabel.setText("Выберите элемент для переименования");
            event.consume();
        }
    }

    private void handleWindowKeys(KeyEvent event) {
        if (event.isConsumed()) return;
        if (event.isAltDown() && event.getCode() == KeyCode.P) {
            addFolder();
            event.consume();
        } else if (event.isAltDown() && event.getCode() == KeyCode.F) {
            addFile();
            event.consume();
        } else if (event.getCode() == KeyCode.F2) {
            TreeItem<FileNode> selected = structureTree.getSelectionModel().getSelectedItem();
            if (selected != null) structureTree.edit(selected);
            else statusLabel.setText("Выберите элемент для переименования");
            event.consume();
        } else if (event.getCode() == KeyCode.DELETE && !(event.getTarget() instanceof TextInputControl)) {
            deleteSelected();
            event.consume();
        }
    }

    void beginDrag(TreeItem<FileNode> item, EditableTreeCell source) {
        finishDrag();
        draggedItem = item;
        draggedCell = source;
        source.setOpacity(0.38);
    }

    void finishDrag() {
        if (draggedCell != null) draggedCell.setOpacity(1.0);
        draggedItem = null;
        draggedCell = null;
        for (Node node : structureTree.lookupAll(".tree-cell")) {
            if (node instanceof EditableTreeCell cell) cell.clearDragState();
        }
    }

    boolean canMoveTo(TreeItem<FileNode> target) {
        return draggedItem != null && target != null && target.getValue().isFolder()
                && target != draggedItem && target != draggedItem.getParent()
                && !isDescendantOf(target, draggedItem)
                && !hasChildWithName(target.getValue(), draggedItem.getValue().getName());
    }

    boolean moveDraggedItemTo(TreeItem<FileNode> destination) {
        if (!canMoveTo(destination)) return false;
        TreeItem<FileNode> item = draggedItem;
        TreeItem<FileNode> oldParent = item.getParent();
        oldParent.getValue().removeChild(item.getValue());
        oldParent.getChildren().remove(item);
        destination.getValue().addChild(item.getValue());
        destination.getChildren().add(item);
        destination.setExpanded(true);
        structureTree.getSelectionModel().select(item);
        structureTree.scrollTo(structureTree.getRow(item));
        statusLabel.setText("Элемент перемещён");
        Platform.runLater(() -> pulseCell(item));
        return true;
    }

    private void pulseCell(TreeItem<FileNode> item) {
        for (Node node : structureTree.lookupAll(".tree-cell")) {
            if (node instanceof EditableTreeCell cell && cell.getTreeItem() == item) {
                cell.playArrivalPulse();
                break;
            }
        }
    }

    private boolean isDescendantOf(TreeItem<FileNode> item, TreeItem<FileNode> possibleAncestor) {
        for (TreeItem<FileNode> parent = item.getParent(); parent != null; parent = parent.getParent()) {
            if (parent == possibleAncestor) return true;
        }
        return false;
    }

    private TreeItem<FileNode> toTreeItem(FileNode node) {
        TreeItem<FileNode> item = new TreeItem<>(node);
        for (FileNode child : node.getChildren()) item.getChildren().add(toTreeItem(child));
        item.setExpanded(true);
        return item;
    }
}
