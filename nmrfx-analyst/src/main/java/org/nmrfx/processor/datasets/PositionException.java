package org.nmrfx.processor.datasets;

import java.io.IOException;

/**
 * @author brucejohnson
 */
class PositionException extends IOException {

    String message;
    long limit;
    int position;
    int[] offsets;

    @Override
    public String getMessage() {
        StringBuilder sBuilder = new StringBuilder();
        sBuilder.append(message).append(" limit: ").append(limit).append(" pos: ").append(position);
        for (int offset : offsets) {
            sBuilder.append(" ").append(offset);
        }
        return sBuilder.toString();
    }

    PositionException(String message, long limit, int position, int... offsets) {
        super();
        this.message = message;
        this.limit = limit;
        this.position = position;
        this.offsets = offsets;
    }

}
