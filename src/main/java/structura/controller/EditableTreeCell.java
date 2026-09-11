package structura.controller;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.WritableImage;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import structura.model.FileNode;

/** Редактируемая строка дерева, безопасная для повторного использования TreeView. */
final class EditableTreeCell extends TreeCell<FileNode> {
    private final MainController controller;
    private final HBox row = new HBox(9);
    private final Label icon = new Label();
    private final Label name = new Label();
    private final TextField editor = new TextField();
    private Timeline shake;
    private ScaleTransition scaleAnimation;

    EditableTreeCell(MainController controller) {
        this.controller = controller;
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("tree-row-content");
        icon.getStyleClass().add("node-icon");
        name.getStyleClass().add("node-name");
        row.getChildren().addAll(icon, name);
        editor.getStyleClass().add("inline-name-editor");

        setOnMouseClicked(event -> {
            if (!isEmpty() && event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                getTreeView().edit(getTreeItem());
                event.consume();
            }
        });
        editor.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                tryCommitRename();
                event.consume();
            } else if (event.getCode() == KeyCode.ESCAPE) {
                cancelEdit();
                getTreeView().requestFocus();
                event.consume();
            }
        });

        setOnDragDetected(event -> {
            TreeItem<FileNode> item = getTreeItem();
            if (item == null || item == getTreeView().getRoot() || isEditing()) return;
            double oldOpacity = row.getOpacity();
            row.setOpacity(0.72);
            WritableImage image = snapshot(new SnapshotParameters(), null);
            row.setOpacity(oldOpacity);
            controller.beginDrag(item, this);
            var dragboard = startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(item.getValue().getName());
            dragboard.setContent(content);
            dragboard.setDragView(image, 18, 14);
            event.consume();
        });
        setOnDragEntered(event -> {
            if (controller.canMoveTo(getTreeItem())) showDropTarget();
        });
        setOnDragOver(event -> {
            if (controller.canMoveTo(getTreeItem())) event.acceptTransferModes(TransferMode.MOVE);
            event.consume();
        });
        setOnDragExited(event -> clearDragState());
        setOnDragDropped(event -> {
            boolean moved = controller.moveDraggedItemTo(getTreeItem());
            event.setDropCompleted(moved);
            clearDragState();
            event.consume();
        });
        setOnDragDone(event -> controller.finishDrag());
    }

    @Override
    protected void updateItem(FileNode item, boolean empty) {
        super.updateItem(item, empty);
        stopTransientEffects();
        setText(null);
        if (empty || item == null) {
            setGraphic(null);
            return;
        }
        icon.setText(item.isFolder() ? "▸" : "◇");
        name.setText(item.getName());
        setGraphic(isEditing() ? editor : row);
    }

    @Override
    public void startEdit() {
        if (isEmpty()) return;
        super.startEdit();
        editor.setText(getItem().getName());
        editor.getStyleClass().remove("rename-error");
        setGraphic(editor);
        Platform.runLater(() -> {
            editor.requestFocus();
            selectEditablePart();
        });
    }

    @Override
    public void cancelEdit() {
        super.cancelEdit();
        editor.getStyleClass().remove("rename-error");
        if (!isEmpty()) {
            name.setText(getItem().getName());
            setGraphic(row);
        }
    }

    private void tryCommitRename() {
        if (controller.rename(getTreeItem(), editor.getText())) {
            super.commitEdit(getItem());
            name.setText(getItem().getName());
            setGraphic(row);
            getTreeView().requestFocus();
        } else {
            playNameError();
        }
    }

    private void selectEditablePart() {
        if (!getItem().isFolder()) {
            String value = editor.getText();
            int dot = value.lastIndexOf('.');
            if (dot > 0) {
                editor.selectRange(0, dot);
                return;
            }
        }
        editor.selectAll();
    }

    private void playNameError() {
        if (!editor.getStyleClass().contains("rename-error")) editor.getStyleClass().add("rename-error");
        if (shake != null) shake.stop();
        shake = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(editor.translateXProperty(), 0)),
                new KeyFrame(Duration.millis(45), new KeyValue(editor.translateXProperty(), -6)),
                new KeyFrame(Duration.millis(90), new KeyValue(editor.translateXProperty(), 6)),
                new KeyFrame(Duration.millis(135), new KeyValue(editor.translateXProperty(), -4)),
                new KeyFrame(Duration.millis(180), new KeyValue(editor.translateXProperty(), 4)),
                new KeyFrame(Duration.millis(225), new KeyValue(editor.translateXProperty(), 0)),
                new KeyFrame(Duration.millis(700), event -> editor.getStyleClass().remove("rename-error"))
        );
        shake.playFromStart();
        editor.requestFocus();
    }

    private void showDropTarget() {
        if (!getStyleClass().contains("drop-target")) getStyleClass().add("drop-target");
        animateScale(1.025, 110);
    }

    void clearDragState() {
        getStyleClass().remove("drop-target");
        if (getScaleX() != 1.0 || getScaleY() != 1.0) animateScale(1.0, 100);
    }

    void playArrivalPulse() {
        clearDragState();
        setScaleX(1.075);
        setScaleY(1.075);
        setEffect(new DropShadow(24, Color.web("#C084FC")));
        animateScale(1.0, 460);
        new Timeline(new KeyFrame(Duration.millis(480), event -> setEffect(null))).play();
    }

    private void animateScale(double target, double milliseconds) {
        if (scaleAnimation != null) scaleAnimation.stop();
        scaleAnimation = new ScaleTransition(Duration.millis(milliseconds), this);
        scaleAnimation.setToX(target);
        scaleAnimation.setToY(target);
        scaleAnimation.play();
    }

    private void stopTransientEffects() {
        if (shake != null) shake.stop();
        if (scaleAnimation != null) scaleAnimation.stop();
        editor.setTranslateX(0);
        editor.getStyleClass().remove("rename-error");
        getStyleClass().remove("drop-target");
        setScaleX(1);
        setScaleY(1);
        setOpacity(1);
        setEffect(null);
    }
}
