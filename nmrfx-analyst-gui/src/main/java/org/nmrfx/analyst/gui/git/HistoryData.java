/*
 * CoMD/NMR Software : A Program for Analyzing NMR Dynamics Data
 * Copyright (C) 2018-2019 Bruce A Johnson
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
package org.nmrfx.analyst.gui.git;

import java.io.File;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;

/**
 *
 * @author mbeckwith
 */

public class HistoryData {

    RevCommit commitInfo;
    String branchName;
    private final StringProperty branch = new SimpleStringProperty();
    private final StringProperty revision = new SimpleStringProperty();
    private final StringProperty date = new SimpleStringProperty();
    private final IntegerProperty index = new SimpleIntegerProperty();
    private final StringProperty user = new SimpleStringProperty();
    private final StringProperty parent = new SimpleStringProperty();
    private final StringProperty message = new SimpleStringProperty();


    public HistoryData(RevCommit commitInfo, int index, String branchName) {
        this.commitInfo = commitInfo;
        this.branchName = branchName;
        setIndex(index);
        setUser();
        setParent();
        setRevision();
        setDate();
        setBranchProperty();
        setMessage();
    }

    public HistoryData(String branchName) {
        this.branchName = branchName;
        setBranchProperty();
    }

    public IntegerProperty indexProperty(){
        return index;
    }
    public int getIndex() {
        return indexProperty().get();
    }
    public void setIndex(int value) {
        indexProperty().set(value);
    }
    
    public String getBranch() {
        return branchName;
    }
    
    public String getShortBranch() {
        String[] branchSplit = branchName.split(File.separator);
        String shortBranch = branchSplit[branchSplit.length - 1];
        return shortBranch;
    }

    public StringProperty userProperty() {
        return user;
    }
    public String getUser() {
        return userProperty().get();
    }
    public void setUser() {
        userProperty().set(commitInfo.getCommitterIdent().getName());
    }

    public StringProperty revisionProperty(){
        return revision;
    }
    public void setRevision() {
        ObjectId objectId = ObjectId.fromString(commitInfo.getName());
        revisionProperty().set(objectId.abbreviate(8).name());
    }
    public String getRevision() {
        return revisionProperty().get();
    }
    public StringProperty dateProperty(){
        return date;
    }
    public void setDate(){
        dateProperty().set(commitInfo.getCommitterIdent().getWhen().toString());
    }
    public String getDate(){
        return dateProperty().get();
    }
    public StringProperty branchProperty(){
        return branch;
    }
    public void setBranchProperty(){
        branchProperty().set(getShortBranch());
    }
    public StringProperty messageProperty() {
        return message;
    }
    public void setMessage(){
        messageProperty().set(commitInfo.getFullMessage());
    }
    public String getMessage(){
        return messageProperty().get();
    }
    public StringProperty parentProperty(){
        return parent;
    }
    public void setParent(){
        String parent = "";
        int nParents = commitInfo.getParentCount();
        if (nParents > 0) {
            parent = commitInfo.getParents()[nParents - 1].getName();
            ObjectId objectId = ObjectId.fromString(parent);
            parent = objectId.abbreviate(8).name();
        }
        parentProperty().set(parent);
    }
    public String getParent(){
        return parentProperty().get();
    }


}


