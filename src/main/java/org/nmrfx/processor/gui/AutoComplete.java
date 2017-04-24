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
package org.nmrfx.processor.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 *
 * @author johnsonb
 */
public class AutoComplete {

    public SetTrie trie;

    /**
     * Initialize a SetTrie with the Operations that are loaded in OperationInfo.
     */
    public AutoComplete() {
        //filter out cascades
        ArrayList<String> operations = new ArrayList<>(OperationInfo.opOrderList);
        //Remove menu names
        operations.removeIf((String s) -> s.contains("Cascade"));

        trie = new SetTrie();
        for (String word : operations) {
            trie.add(word);
        }

    }

    public class SetTrie {

        private final TreeSet<String> words;

        public SetTrie() {
            words = new TreeSet<>();
        }

        public void add(String line) {
            words.add(line);
        }

        /**
         * Checks to see if the prefix matches any existing operations.
         *
         * @param prefix A String that is possibly a prefix of an operation.
         * @return true if there exists a word in the Trie that matches the prefix.
         */
        public boolean match(String prefix) {
            Set<String> tailSet = words.tailSet(prefix);
            for (String tail : tailSet) {
                if (tail.startsWith(prefix)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Return a list of autocompleted Operations, given a prefix String.
         *
         * @param prefix First n letters of an Operation
         * @return All matching Operations to the prefix
         */
        public List<String> autoComplete(String prefix) {
            List<String> completions = new ArrayList<>();
            Set<String> tailSet = words.tailSet(prefix);
            for (String tail : tailSet) {
                if (tail.startsWith(prefix)) {
                    completions.add(tail);
                } else {
                    break;
                }
            }
            return completions;
        }
    }
}
