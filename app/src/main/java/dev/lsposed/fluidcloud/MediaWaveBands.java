package dev.lsposed.fluidcloud;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/** Seven shared boundaries define six contiguous bands, from left to right. */
public record MediaWaveBands(List<Integer> edges) {
    public static final String KEY = "wave_band_edges";
    public static final int EDGE_COUNT = 7;
    public static final int MIN_HZ = 20;
    public static final int MAX_HZ = 24000;
    public static final MediaWaveBands DEFAULTS = new MediaWaveBands(List.of(40, 125, 315, 800, 2000, 5000, 16000));

    public MediaWaveBands {
        edges = List.copyOf(edges);
        if (edges.size() != EDGE_COUNT) throw new IllegalArgumentException("Expected seven frequency boundaries");
        int previous = MIN_HZ - 1;
        for (int edge : edges) {
            if (edge <= previous || edge > MAX_HZ) throw new IllegalArgumentException("Invalid frequency boundary");
            previous = edge;
        }
    }

    public int edgeHz(int index) {
        return edges.get(index);
    }

    public String encode() {
        StringJoiner encoded = new StringJoiner(",");
        for (int edge : edges) encoded.add(Integer.toString(edge));
        return encoded.toString();
    }

    public static MediaWaveBands parse(String encoded) {
        if (encoded == null) return null;
        String[] fields = encoded.split(",", EDGE_COUNT + 1);
        if (fields.length != EDGE_COUNT) return null;
        var edges = new ArrayList<Integer>(EDGE_COUNT);
        for (String field : fields) {
            String value = field.strip();
            if (!value.matches("[0-9]{1,5}")) return null;
            edges.add(Integer.parseInt(value));
        }
        try {
            return new MediaWaveBands(edges);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    public static MediaWaveBands from(Map<String, ?> values) {
        Object encoded = values.get(KEY);
        MediaWaveBands bands = encoded instanceof String text ? parse(text) : null;
        return bands == null ? DEFAULTS : bands;
    }
}
