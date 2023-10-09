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
import java.util.Date;
import org.eclipse.jgit.revwalk.RevCommit;

/**
 *
 * @author mbeckwith
 */

public class HistoryData {

    int index;
    RevCommit commitInfo;
    String branchName;

    public HistoryData(RevCommit commitInfo, int index, String branchName) {
        this.commitInfo = commitInfo;
        this.index = index;
        this.branchName = branchName;
    }

    public int getIndex() {
        return index;
    }
    
    public String getBranch() {
        return branchName;
    }
    
    public String getShortBranch() {
        String[] branchSplit = branchName.split(File.separator);
        String shortBranch = branchSplit[branchSplit.length - 1];
        return shortBranch;
    }

    public Date getDate() {
        return commitInfo.getCommitterIdent().getWhen();
    }

    public String getRevision() {
        return commitInfo.getName();
    }

    public String getUser() {
        return commitInfo.getCommitterIdent().getName();
    }

    public String getMessage() {
        return commitInfo.getFullMessage();
    }
    
    public String getParent() {
        String parent = "";
        int nParents = commitInfo.getParentCount();
        if (nParents > 0) {
            parent = commitInfo.getParents()[nParents - 1].getName();
        }
        return parent;
    }

}


