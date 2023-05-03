package org.nmrfx.utilities;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author brucejohnson
 */
public class RemoteDataset {

    private static final Logger log = LoggerFactory.getLogger(RemoteDataset.class);
    static List<RemoteDataset> datasets = new ArrayList<>();

    @Expose
    private String path;
    @Expose
    private String type;
    @Expose
    private String user;
    @Expose
    private String seq;
    @Expose
    private Double sf;
    @Expose
    private String time;
    @Expose
    private String tn;
    @Expose
    private String sol;
    @Expose
    private Double te;
    @Expose
    private String pos;
    @Expose
    private Integer nd;
    @Expose
    private Integer nv;
    @Expose
    private String text;
    @Expose
    private String vnd;
    private String nb;
    @Expose
    private String sample;
    @Expose
    private String iso;
    @Expose
    private String hashKey;
    private boolean present;
    private String processed;

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

    public boolean isPresent() {
        return present;
    }

    public void setPresent(boolean state) {
        present = state;
    }

    public String getProcessed() {
        return processed == null ? "" : processed;
    }

    public void setProcessed(String fileName) {
        processed = fileName;
    }

    /**
     * @return the hashKey
     */
    public String getHashKey() {
        return hashKey;
    }

    /**
     * @param path the path to set
     */
    public void setPath(String path) {
        this.path = path;
    }

    /**
     * @param type the type to set
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * @param user the user to set
     */
    public void setUser(String user) {
        this.user = user;
    }

    /**
     * @param seq the seq to set
     */
    public void setSeq(String seq) {
        this.seq = seq;
    }

    /**
     * @param sf the sf to set
     */
    public void setSf(Double sf) {
        this.sf = sf;
    }

    /**
     * @param time the time to set
     */
    public void setTime(String time) {
        this.time = time;
    }

    /**
     * @param tn the tn to set
     */
    public void setTn(String tn) {
        this.tn = tn;
    }

    /**
     * @param sol the sol to set
     */
    public void setSol(String sol) {
        this.sol = sol;
    }

    /**
     * @param te the te to set
     */
    public void setTe(Double te) {
        this.te = te;
    }

    /**
     * @param pos the pos to set
     */
    public void setPos(String pos) {
        this.pos = pos;
    }

    /**
     * @param nd the nd to set
     */
    public void setNd(Integer nd) {
        this.nd = nd;
    }

    /**
     * @param nv the nv to set
     */
    public void setNv(Integer nv) {
        this.nv = nv;
    }

    /**
     * @param text the text to set
     */
    public void setText(String text) {
        this.text = text;
    }

    /**
     * @param vnd the vnd to set
     */
    public void setVnd(String vnd) {
        this.vnd = vnd;
    }

    /**
     * @param nb the nb to set
     */
    public void setNb(String nb) {
        this.nb = nb;
    }

    /**
     * @param sample the sample to set
     */
    public void setSample(String sample) {
        this.sample = sample;
    }

    /**
     * @param iso the iso to set
     */
    public void setIso(String iso) {
        this.iso = iso;
    }

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

    public String toJson() {
        Gson gson = new GsonBuilder().
                excludeFieldsWithoutExposeAnnotation().create();
        return gson.toJson(this);
    }

    public static String toJson(List<RemoteDataset> items) {
        Gson gson = new GsonBuilder().
                excludeFieldsWithoutExposeAnnotation().setPrettyPrinting().create();
        return gson.toJson(items);
    }

    public static void loadFromFile(File file) {
        datasets.clear();
        Gson gson = new Gson();
        Charset charset = Charset.forName("US-ASCII");
        try (var reader = Files.newBufferedReader(file.toPath(), charset)) {
            String line;
            while ((line = reader.readLine()) != null) {
                int equalsIndex = line.indexOf("= ");
                String key = line.substring(0, equalsIndex);
                String jsonStr = line.substring(equalsIndex + 2);
                RemoteDataset dataset = datasetFromJson(key, jsonStr, gson);
                datasets.add(dataset);
            }
        } catch (IOException x) {
           log.warn(x.getMessage(), x);
        }
    }

    public static void loadListFromFile(File file) throws IOException {
        datasets.clear();
        Gson gson = new Gson();
        String jsonStr = Files.readString(file.toPath());
        datasets = datasetsFromJson(jsonStr);
    }

    public static void saveItems(Path outPath, List<RemoteDataset> items) {
        String jsonStr = toJson(items);
        try {
            Files.writeString(outPath, jsonStr);
        } catch (IOException ex) {
            log.warn(ex.getMessage(), ex);
        }

    }

    public static List<RemoteDataset> getDatasets() {
        return datasets;
    }

}
