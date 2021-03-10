
package org.nmrfx.utilities;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author brucejohnson
 */
public class RemoteDataset {

    /**
     * @return the path
     */
    public String getPath() {
        return path;
    }

    /**
     * @return the type
     */
    public String getType() {
        return type;
    }

    /**
     * @return the user
     */
    public String getUser() {
        return user;
    }

    /**
     * @return the seq
     */
    public String getSeq() {
        return seq;
    }

    /**
     * @return the sf
     */
    public Double getSf() {
        return sf;
    }

    /**
     * @return the time
     */
    public String getTime() {
        return time;
    }

    /**
     * @return the tn
     */
    public String getTn() {
        return tn;
    }

    /**
     * @return the sol
     */
    public String getSol() {
        return sol;
    }

    /**
     * @return the te
     */
    public Double getTe() {
        return te;
    }

    /**
     * @return the pos
     */
    public String getPos() {
        return pos;
    }

    /**
     * @return the nd
     */
    public Integer getNd() {
        return nd;
    }

    /**
     * @return the nv
     */
    public Integer getNv() {
        return nv;
    }

    /**
     * @return the text
     */
    public String getText() {
        return text;
    }

    /**
     * @return the vnd
     */
    public String getVnd() {
        return vnd;
    }

    /**
     * @return the nb
     */
    public String getNb() {
        return nb;
    }

    /**
     * @return the sample
     */
    public String getSample() {
        return sample;
    }

    /**
     * @return the iso
     */
    public String getIso() {
        return iso;
    }

    /**
     * @return the hashKey
     */
    public String getHashKey() {
        return hashKey;
    }

    static List<RemoteDataset> datasets = new ArrayList<>();

    private String path;
    private String type;
    private String user;
    private String seq;
    private Double sf;
    private String time;
    private String tn;
    private String sol;
    private Double te;
    private String pos;
    private Integer nd;
    private Integer nv;
    private String text;
    private String vnd;
    private String nb;
    private String sample;
    private String iso;
    private String hashKey;

    @Override
    public String toString() {
        return "RemoteDataset{" + "path=" + path + ", type=" + type + ", user=" + user + ", seq=" + seq + ", sf=" + sf + ", time=" + time + ", tn=" + tn + ", sol=" + sol + ", te=" + te + ", pos=" + pos + ", nd=" + nd + ", nv=" + nv + ", text=" + text + ", vnd=" + vnd + ", nb=" + nb + ", sample=" + sample + ", iso=" + iso + '}';
    }

    public static List<RemoteDataset> datasetsFromJson(String jsonString) {
        Gson gson = new Gson();
        List<RemoteDataset> list = gson.fromJson(jsonString, new TypeToken<List<RemoteDataset>>() {
        }.getType());
        return list;
    }

    public static RemoteDataset datasetFromJson(String hashKey, String jsonString) {
        Gson gson = new Gson();
        return datasetFromJson(hashKey, jsonString, gson);
    }

    public static RemoteDataset datasetFromJson(String hashKey, String jsonString, Gson gson) {
        RemoteDataset dataset = gson.fromJson(jsonString, RemoteDataset.class);
        dataset.hashKey = hashKey;
        return dataset;
    }

    public static void loadFromFile(File file) {
        datasets.clear();
        Gson gson = new Gson();
        Charset charset = Charset.forName("US-ASCII");
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), charset)) {
            String line = null;
            while ((line = reader.readLine()) != null) {
                int equalsIndex = line.indexOf("= ");
                String key = line.substring(0, equalsIndex);
                String jsonStr = line.substring(equalsIndex + 2);
                RemoteDataset dataset = datasetFromJson(key, jsonStr, gson);
                datasets.add(dataset);
            }
        } catch (IOException x) {
            System.err.format("IOException: %s%n", x);
        }
    }
    
    public static List<RemoteDataset> getDatasets() {
        return datasets;
    }

}
