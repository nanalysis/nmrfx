package org.nmrfx.utilities;

import berlin.yuna.typemap.model.LinkedTypeMap;
import berlin.yuna.typemap.model.TypeList;
import org.apache.hc.core5.http.HttpStatus;
import org.nmrfx.fxutil.Fx;
import org.nmrfx.star.BMRBio;
import org.nmrfx.utils.GUIUtils;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class BMRBSearchResult {
    private String entryID;
    private String releaseDate;
    private List<HashMap> dataSummary;
    private String title;
    private List<String> authors;

    public BMRBSearchResult(LinkedTypeMap entry) {
        entryID = entry.get(String.class, "value");
        releaseDate = entry.get(String.class, "sub_date");
        dataSummary = entry.getList(HashMap.class, "data_types");
        title = entry.get(String.class, "label");
        authors = entry.get(List.class, "authors");
    }

    public String getEntryID() {
        return entryID;
    }

    public void setEntryID(String entryID) {
        this.entryID = entryID;
    }

    public String getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(String releaseDate) {
        this.releaseDate = releaseDate;
    }

    public List<HashMap >getDataSummary() {
        return dataSummary;
    }

    public void setDataSummary(List<HashMap> dataSummary) {
        this.dataSummary = dataSummary;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<String> getAuthors() {
        return authors;
    }

    public void setAuthors(List<String> authors) {
        this.authors = authors;
    }

    public static List<BMRBSearchResult> getSearchResults(String searchString) throws ExecutionException, InterruptedException {
        CompletableFuture<HttpResponse<String>> response = null;
        try {
            response = BMRBio.fetchSearchResults(searchString);
        } catch (Exception e) {
            return null;
        }
        CompletableFuture<TypeList> future = response.thenApply(r -> {
                if (r.statusCode() != HttpStatus.SC_OK) {
                    Fx.runOnFxThread(() -> GUIUtils.warn("BMRB Search", "Unsuccessful search"));
                    return null;
                }
                return new TypeList(r.body());
        });
        TypeList typeList = future.get();
        if (typeList == null) {
            return null;
        }

        List<BMRBSearchResult> resultList = new ArrayList<>();
        for (var entry : typeList) {
            LinkedTypeMap typeMap = typeList.getMap(entry);
            BMRBSearchResult result = new BMRBSearchResult(typeMap);
            resultList.add(result);
            }
        return resultList;
    }
}
