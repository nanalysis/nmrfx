package org.nmrfx.processor.gui;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.effect.InnerShadow;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Callback;
import org.controlsfx.control.ListSelectionView;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.gui.spectra.DatasetAttributes;
import org.nmrfx.utilities.DictionarySort;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class DatasetView {
    FXMLController fxmlController;
    ListSelectionView<String> datasetView;
    ListChangeListener<String> datasetTargetListener;
    Integer startIndex = null;
    boolean moveItemIsSelected = false;
    Node startNode = null;
    private final ListChangeListener<DatasetAttributes> datasetAttributesListChangeListener = this::chartDatasetAttributesListener;

    public DatasetView(FXMLController fxmlController, ContentController controller) {
        this.fxmlController = fxmlController;
        datasetView = controller.datasetView;
        datasetTargetListener = (ListChangeListener.Change<? extends String> c) -> {
            PolyChart chart = this.fxmlController.getActiveChart();
            // Must remove this listener since it calls updateDatasetView, which this listener may
            // already have been triggered from, resulting in an UnsupportedOperationException on the datasetView(ListSelectionView)
            chart.getDatasetAttributes().removeListener(datasetAttributesListChangeListener);
            updateChartDatasets();
            chart.getDatasetAttributes().addListener(datasetAttributesListChangeListener);
        };
        datasetView.getTargetItems().addListener(datasetTargetListener);
        this.fxmlController.getActiveChart().getDatasetAttributes().addListener(datasetAttributesListChangeListener);
        PolyChartManager.getInstance().activeChartProperty().addListener(new WeakChangeListener<>((observable, oldValue, newValue) -> {

            if (oldValue != null && this.fxmlController.getCharts().contains(oldValue)) {
                oldValue.getDatasetAttributes().removeListener(datasetAttributesListChangeListener);
            }
            if (newValue != null && this.fxmlController.getCharts().contains(newValue)) {
                newValue.getDatasetAttributes().addListener(datasetAttributesListChangeListener);
                updateDatasetView();
            }
        }));


        initView();
    }

    void initView() {
        datasetView.setCellFactory(new Callback<>() {
            @Override
            public ListCell<String> call(ListView<String> p) {
                return new DatasetListCell<>(datasetView) {
                    @Override
                    public void updateItem(String s, boolean empty) {
                        super.updateItem(s, empty);
                        if (empty || s == null) {
                            setText(null);
                            setGraphic(null);
                        } else {
                            Rectangle rect = new Rectangle(10, 10);
                            rect.setFill(Color.BLACK);
                            getDatasetAttributes(s).ifPresent(datasetAttributes -> rect.setFill(datasetAttributes.getPosColor()));
                            setText(s);
                            setGraphic(rect);
                        }
                    }
                };
            }
        });
    }

    Optional<DatasetAttributes> getDatasetAttributes(String name) {
        PolyChart chart = fxmlController.getActiveChart();
        return chart.getDatasetAttributes().stream().filter(dAttr -> dAttr.getDataset().getName().equals(name)).findFirst();
    }

    public void chartDatasetAttributesListener(ListChangeListener.Change<? extends DatasetAttributes> change) {
        updateDatasetView();
    }

    public void updateDatasetView() {
        datasetView.getTargetItems().removeListener(datasetTargetListener);
        ObservableList<String> datasetsTarget = datasetView.getTargetItems();
        ObservableList<String> datasetsSource = datasetView.getSourceItems();
        datasetsTarget.clear();
        datasetsSource.clear();
        PolyChart chart = fxmlController.getActiveChart();
        for (DatasetAttributes obj : chart.getDatasetAttributes()) {
            datasetsTarget.add(obj.getDataset().getName());
        }
        DictionarySort<DatasetBase> sorter = new DictionarySort<>();
        Dataset.datasets().stream().sorted(sorter).forEach(d -> {
            if (!datasetsTarget.contains(d.getName())) {
                datasetsSource.add(d.getName());
            }
        });
        datasetView.getTargetItems().addListener(datasetTargetListener);
    }

    private void updateChartDatasets() {
        ObservableList<String> datasetTargets = datasetView.getTargetItems();
        PolyChart chart = fxmlController.getActiveChart();
        chart.updateDatasets(datasetTargets);
        if (datasetTargets.isEmpty()) {
            chart.removeProjections();
            chart.getCrossHairs().hideAll();
        } else {
            chart.updateProjections();
            chart.updateProjectionBorders();
            chart.updateProjectionScale();
        }
        try {
            // TODO NMR-6048: remove sleep once threading issue fixed
            TimeUnit.MILLISECONDS.sleep(200);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        chart.refresh();
    }


    class DatasetListCell<T> extends ListCell<T> implements ChangeListener<String> {

        private final ListSelectionView<String> listView;

        @Override
        public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
        }

        DatasetListCell(ListSelectionView<String> listView) {
            this.listView = listView;
            this.setOnDragDetected(event -> {
                startIndex = indexProperty().get();
                Dragboard db = startDragAndDrop(TransferMode.COPY);

                /* Put a string on a dragboard */
                ClipboardContent content = new ClipboardContent();
                String sourcetext = getText();
                moveItemIsSelected = isSelectedDataset(sourcetext);
                content.putString(getText());
                db.setContent(content);
                startNode = getParent();
                event.consume();
            });
            this.setOnDragDone(Event::consume);
            this.setOnDragDropped(event -> {
                Object target = event.getGestureTarget();
                if (target instanceof DatasetListCell targetCell) {
                    String targetText = targetCell.getText();
                    moveItem(targetText, getParent());
                }
                event.consume();
            });
            this.setOnDragEntered(Event::consume);
            this.setOnDragExited(event -> {
                Object target = event.getTarget();
                if (target instanceof DatasetListCell targetCell) {
                    targetCell.setEffect(null);
                }
                event.consume();
            });
            this.setOnDragOver(event -> {
                event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
                Object target = event.getGestureTarget();
                if (target instanceof DatasetListCell targetCell) {
                    InnerShadow is = new InnerShadow();
                    is.setOffsetX(1.0);
                    is.setColor(Color.web("#666666"));
                    is.setOffsetY(1.0);
                    targetCell.setEffect(is);
                }
                event.consume();
            });
        }


        boolean isSelectedDataset(String text) {
            for (String item : listView.getTargetItems()) {
                if (item.equals(text)) {
                    return true;
                }
            }
            return false;

        }

        void moveItem(String targetText, Node endNode) {
            datasetView.getTargetItems().removeListener(datasetTargetListener);
            final boolean targetItemIsSelected;
            List<String> moveFromItems;
            List<String> moveToItems;
            String startItem;
            try {
                if (moveItemIsSelected) {
                    targetItemIsSelected = endNode == startNode;
                } else {
                    targetItemIsSelected = endNode != startNode;
                }

                if (moveItemIsSelected) {
                    moveFromItems = listView.getTargetItems();
                } else {
                    moveFromItems = listView.getSourceItems();
                }
                if (targetItemIsSelected) {
                    moveToItems = listView.getTargetItems();
                } else {
                    moveToItems = listView.getSourceItems();
                }
                startItem = moveFromItems.get(startIndex);
                moveFromItems.remove(startIndex.intValue());
            } finally {
                datasetView.getTargetItems().addListener(datasetTargetListener);
            }
            if ((targetText == null) || (targetText.isEmpty())) {
                moveToItems.add(startItem);
            } else {
                int index = moveToItems.indexOf(targetText);
                if (targetItemIsSelected == moveItemIsSelected) {
                    if (index >= startIndex) {
                        index++;
                    }
                }
                moveToItems.add(index, startItem);
            }
            refreshAction();
        }
    }

    private void refreshAction() {
    }
}

