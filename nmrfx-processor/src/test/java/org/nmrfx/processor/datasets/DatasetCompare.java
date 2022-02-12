package org.nmrfx.processor.datasets;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class DatasetCompare {

    public static long compare(File refFile, File testFile) throws IOException {
        long refLen = refFile.length();
        long testLen = testFile.length();
        long result = 0;

        if (refLen != testLen) {
            result = Math.min(refLen, testLen);
        } else {
            RandomAccessFile refRAFile = new RandomAccessFile(refFile, "r");
            RandomAccessFile testRAFile = new RandomAccessFile(testFile, "r");

            MappedByteBuffer refBuffer = refRAFile.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, refLen);
            MappedByteBuffer testBuffer = testRAFile.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, testLen);
            if (refBuffer.compareTo(testBuffer) != 0) {
                refBuffer.position(0);
                testBuffer.position(0);
                for (int i = 0; i < testLen; i++) {
                    if (refBuffer.get(i) != testBuffer.get(i)) {
                        // if match fails at position 0 return -1
                        result = i == 0 ? -1 : i;
                        break;
                    }
                }
            }
        }
        return result;
    }

    public static long compareFloat(File refFile, File testFile) throws IOException {
        long refLen = refFile.length();
        long testLen = testFile.length();
        long result = 0;

        if (refLen != testLen) {
            result = Math.min(refLen, testLen);
        } else {
            RandomAccessFile refRAFile = new RandomAccessFile(refFile, "r");
            RandomAccessFile testRAFile = new RandomAccessFile(testFile, "r");

            MappedByteBuffer refBuffer = refRAFile.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, refLen);
            MappedByteBuffer testBuffer = testRAFile.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, testLen);
            var refFloat = refBuffer.asFloatBuffer();
            var testFloat = testBuffer.asFloatBuffer();
                refFloat.position(0);
                testFloat.position(0);
                for (int i = 0; i < testLen / Float.BYTES; i++) {
                    if (refFloat.get(i) != testFloat.get(i)) {
                        // if match fails at position 0 return -1
                        result = i == 0 ? -1 : i;
                        break;
                    }
            }
        }
        return result;
    }
}
