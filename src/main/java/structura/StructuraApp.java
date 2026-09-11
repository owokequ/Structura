package structura;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.IOException;

public class StructuraApp extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        // FXMLLoader читает FXML-разметку и создаёт описанные в ней JavaFX-компоненты.
        // Ресурс начинается с '/', потому что ищется от корня classpath, а не рядом с классом.
        FXMLLoader loader = new FXMLLoader(
                StructuraApp.class.getResource("/structura/view/main-view.fxml")
        );

        // load() создаёт корневой узел и связывает его с MainController,
        // указанным в атрибуте fx:controller файла FXML.
        Parent root = loader.load();

        // Scene — содержимое окна; заданный размер используется при первом показе приложения.
        Scene scene = new Scene(root, 1100, 720);

        // Stage — само окно приложения. Сначала ему передаём сцену и заголовок,
        // затем show() делает окно видимым для пользователя.
        stage.setTitle("Structura");
        stage.setMinWidth(940);
        stage.setMinHeight(640);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        // launch передаёт управление JavaFX и запускает приложение в его UI-потоке.
        // Компоненты интерфейса важно создавать и изменять именно в этом потоке.
        launch(args);
    }
}
