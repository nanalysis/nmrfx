package org.nmrfx.ribbon;

import com.pixelduke.control.Ribbon;
import de.jensd.fx.glyphs.GlyphIcons;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TabPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.util.Duration;

import java.util.Optional;

public class NmrFxRibbon extends Ribbon {
    private final Node iconHide = glyphIconToNode(FontAwesomeIcon.ANGLE_DOUBLE_UP);
    private final Node iconShow = glyphIconToNode(FontAwesomeIcon.ANGLE_DOUBLE_DOWN);
    private final Button toggleButton = new Button("", iconHide);

    public NmrFxRibbon() {
        setupToggleButton();
    }

    private void setupToggleButton() {
        toggleButton.setOnAction(e -> toggleTabVisibility());

        //TODO find a way to show this button at the same level as group names instead of the quick access bar
        //this may require forking fxribbon
        getQuickAccessBar().getRightButtons().add(toggleButton);
    }

    public void hideTabs() {
        findTabPane().filter(Node::isVisible)
                .ifPresent(tabs -> toggleTabVisibility(false, Duration.millis(1)));
    }

    private void toggleTabVisibility() {
        findTabPane().ifPresent(tabs -> toggleTabVisibility(!tabs.isVisible(), Duration.millis(250)));
    }

    // find the TabPane and toggle its visible flag, adjust button icon accordingly
    private void toggleTabVisibility(boolean show, Duration transitionDuration) {
        findTabPane().ifPresent(tabs -> {
            var transition = new TranslateTransition(transitionDuration, tabs);
            int direction = show ? 1 : -1;
            transition.setByY(tabs.getHeight() * direction);
            if (show) {
                // show before transition, otherwise nothing will appear
                tabs.setVisible(true);
                tabs.setManaged(true);
                toggleButton.setGraphic(iconHide);
            } else {
                // fully hide after transition
                transition.setOnFinished(event -> {
                    tabs.setVisible(false);
                    tabs.setManaged(false);
                    toggleButton.setGraphic(iconShow);
                });
            }

            transition.play();
        });
    }

    public Optional<TabPane> findTabPane() {
        // Ribbon structure: {outer-container:VBox {QuickAccessBar, TabPane}}
        return getChildrenUnmodifiable().stream()
                .filter(node -> node instanceof VBox)
                .flatMap(node -> ((VBox) node).getChildrenUnmodifiable().stream())
                .filter(node -> node instanceof TabPane)
                .map(node -> (TabPane) node)
                .findFirst();
    }

    private static Node glyphIconToNode(GlyphIcons icon) {
        Text text = new Text(icon.characterToString());
        text.getStyleClass().add("glyph-icon");
        text.setStyle(String.format("-fx-font-family: %s; -fx-font-size: %s;", icon.getFontFamily(), "16px"));
        return text;
    }
}
