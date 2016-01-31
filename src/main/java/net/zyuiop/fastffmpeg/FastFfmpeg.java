package net.zyuiop.fastffmpeg;

import javafx.application.Application;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.ProgressBarTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static net.zyuiop.fastffmpeg.FFMPEGConverter.ConversionThread;

/**
 * @author zyuiop
 */
public class FastFfmpeg extends Application {
	private FFMPEGConverter converter;

	public static void main(String[] args) throws IOException {
		launch(args);
	}

	@Override
	public void start(Stage stage) throws Exception {
		TableColumn<ConversionThread, String> sourceCol = new TableColumn<>("Source");
		sourceCol.setPrefWidth(300);
		sourceCol.setCellValueFactory((TableColumn.CellDataFeatures<ConversionThread, String> param) ->
				new ReadOnlyStringWrapper(param.getValue().getPath())
		);

		TableColumn<ConversionThread, String> targetCol = new TableColumn<>("Destination");
		targetCol.setPrefWidth(300);
		targetCol.setCellValueFactory((TableColumn.CellDataFeatures<ConversionThread, String> param) ->
				new ReadOnlyStringWrapper(param.getValue().getOutput())
		);

		TableColumn<ConversionThread, String> encoderCol = new TableColumn<>("Encodeur");
		encoderCol.setPrefWidth(75);
		encoderCol.setCellValueFactory((TableColumn.CellDataFeatures<ConversionThread, String> param) ->
				new ReadOnlyStringWrapper(param.getValue().getEncoder())
		);

		TableColumn<ConversionThread, Double> progressCol = new TableColumn<>("Progression");
		progressCol.setPrefWidth(300);
		progressCol.setCellValueFactory(new PropertyValueFactory<>("progress"));
		progressCol.setCellFactory(ProgressBarTableCell.<ConversionThread>forTableColumn());

		TableColumn<ConversionThread, String> statusCol = new TableColumn<>("État");
		statusCol.setCellValueFactory(new PropertyValueFactory<>("message"));
		statusCol.setPrefWidth(150);

		TableView<ConversionThread> view = new TableView<>();
		view.getColumns().addAll(sourceCol, targetCol, encoderCol, progressCol, statusCol);

		ChoiceBox<Encoders> choiceBox = new ChoiceBox<>(FXCollections.observableArrayList(Encoders.values()));
		choiceBox.setConverter(new StringConverter<Encoders>() {
			@Override
			public String toString(Encoders object) {
				return object.getDisplay();
			}

			@Override
			public Encoders fromString(String string) {
				return Encoders.getByDisplay(string);
			}
		});

		choiceBox.setValue(Encoders.MP4);

		Label label = new Label("Encodeur :");
		Button button = new Button("Ajouter des fichiers...");
		button.setOnMouseClicked(event -> {
			FileChooser chooser = new FileChooser();
			chooser.setTitle("Choisir des vidéos");
			List<File> files = chooser.showOpenMultipleDialog(stage.getOwner());
			if (files == null)
				return;

			files.forEach(file -> {
				String path = file.getAbsolutePath();
				String[] parts = path.split("\\.");
				parts[parts.length - 1] = choiceBox.getValue().getFileExtension();
				parts[parts.length - 2] = parts[parts.length - 2] + "-converted";
				String target = StringUtils.join(parts, ".");

				view.getItems().add(converter.convert(path, target, choiceBox.getValue().getCodev()));
			});
		});

		final HBox hb = new HBox();
		hb.setSpacing(5);
		hb.setAlignment(Pos.CENTER);
		hb.getChildren().addAll(label, choiceBox, button);

		final VBox vb = new VBox();
		vb.setSpacing(5);
		vb.getChildren().addAll(view, hb);

		BorderPane root = new BorderPane();
		root.setCenter(vb);
		stage.setScene(new Scene(root));
		stage.setTitle("Magic Converter");
		//stage.show();

		int cores = Runtime.getRuntime().availableProcessors() / 2;
		converter = new FFMPEGConverter(cores, stage);
	}

	@Override
	public void stop() throws Exception {
		converter.close();
		System.exit(0);
	}
}
