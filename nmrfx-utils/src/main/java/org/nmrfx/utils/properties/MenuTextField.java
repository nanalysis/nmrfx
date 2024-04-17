/*
 * NMRFx Processor : A Program for Processing NMR Data
 * Copyright (C) 2004-2017 One Moon Scientific, Inc., Westfield, N.J., USA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.utils.properties;

import javafx.scene.control.MenuButton;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Font;
import org.controlsfx.control.textfield.CustomTextField;

/**
 * @author brucejohnson
 */
public class MenuTextField extends GridPane {

    private final CustomTextField textField = new CustomTextField();
    private final MenuButton menuButton = new MenuButton("");

    public MenuTextField() {
        super();
        textField.setFont(new Font(11));
        textField.setPrefWidth(140);
        init();
    }

    private void init() {
        add(textField, 0, 0);
        add(menuButton, 1, 0);
    }

    public CustomTextField getTextField() {
        return textField;
    }

    /**
     * @return the menuButton
     */
    public MenuButton getMenuButton() {
        return menuButton;
    }

    public void setPrompt(String text) {
        textField.setPromptText(text);
    }

    public void setText(String text) {
        if (text == null) {
            text = "";
        }
        text = text.trim();
        textField.setText(text);
    }

}
