package org.nmrfx.processor.datasets;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.util.MultidimensionalCounter;

import java.util.*;

public class DatasetGroupIndex {
    final int[] indices;
    final String groupIndex;

    public DatasetGroupIndex(int[] indices, String groupIndex) {
        this.indices = indices.clone();
        this.groupIndex = groupIndex;
    }

    // parse DatasetGroupIndex in format like 4,7,"RI"
    public DatasetGroupIndex(String strValue) {
        String[] fields = strValue.split(",");
        indices = new int[fields.length - 1];
        String lastValue = "";
        for (int i = 0; i < fields.length; i++) {
            if (i < indices.length) {
                int value = Integer.parseInt(fields[i]);
                if (value > 0) {
                    value--;
                }
                indices[i] = value;
            } else {
                lastValue = StringUtils.strip(fields[i], "'\"");
                break;
            }
        }
        groupIndex = lastValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DatasetGroupIndex that = (DatasetGroupIndex) o;
        return groupIndex == that.groupIndex && Arrays.equals(indices, that.indices);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(groupIndex);
        result = 31 * result + Arrays.hashCode(indices);
        return result;
    }

    public String toIndexString() {
        boolean first = true;
        StringBuilder sBuilder = new StringBuilder();
        for (int index : indices) {
            if (!first) {
                sBuilder.append(",");
            } else {
                first = false;
            }
            if (index >= 0) {
                index++;
            }
            sBuilder.append(index);
        }
        sBuilder.append(",").append("'").append(groupIndex).append("'");
        return sBuilder.toString();
    }

    public String toString() {
        return toIndexString().replace("-1", "*");
    }

    public int[] getIndices() {
        return indices;
    }

    public String getGroupIndex() {
        return groupIndex;
    }

    // build string for putting in process.py script markrows command
    // looks like "[3,4,"RI"],[5,10,"RR"], where the numbers are row, plane,... and the
    // string is the real /imaginary state of the vector where highest deviation was found
    public static Optional<String> getSkipString(Collection<DatasetGroupIndex> groups) {
        Optional<String> result = Optional.empty();
        if (!groups.isEmpty()) {
            StringBuilder sBuilder = new StringBuilder();
            sBuilder.append("[");
            for (DatasetGroupIndex group : groups) {
                if (sBuilder.length() != 1) {
                    sBuilder.append("],[");
                }
                sBuilder.append(group.toIndexString());
            }
            sBuilder.append("]");
            result = Optional.of(sBuilder.toString());
        }
        return result;
    }

    public List<int[]> groupToIndices() {
        List<int[]> result = new ArrayList<>();
        int[] sizes = new int[indices.length];
        for (int i = 0; i < indices.length; i++) {
            sizes[i] = 2;
        }
        MultidimensionalCounter counter = new MultidimensionalCounter(sizes);
        var iterator = counter.iterator();
        while (iterator.hasNext()) {
            iterator.next();
            int[] counts = iterator.getCounts();
            int[] cIndices = new int[indices.length];
            for (int k = 0; k < indices.length; k++) {
                if (indices[k] >= 0) {
                    cIndices[k] = indices[k] * sizes[k] + counts[k];
                } else {
                    cIndices[k] = -1;
                }
            }
            result.add(cIndices);
        }
        return result;
    }

}
