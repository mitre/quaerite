package org.mitre.quaerite.features;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PF implements FeatureSet {

    public static PF EMPTY = new PF(Collections.emptyList(), Collections.emptyList());

    private static final String PF = "pf";

    List<String> features;
    List<String> fields = new ArrayList<>();
    List<Float> weights = new ArrayList<>();

    public PF(List<String> fields, List<Float> weights) {
        this.fields = fields;
        this.weights = weights;
        init();
    }

    private void init() {
        features = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        recurse(0, fields, weights, sb);
    }

    private void recurse(int i, List<String> fields, List<Float> weights, StringBuilder sb) {
        if (i >= fields.size()) {
            return;
        }
        String base = sb.toString();
        for (Float f : weights) {
            StringBuilder feature = new StringBuilder(base);
            if (f > 0.0f) {
                if (feature.length() > 0) {
                    feature.append(",");
                }
                feature.append(fields.get(i)).append("^").append(f);
                features.add(feature.toString());
            }
            recurse(i+1, fields, weights, feature);
        }
    }


    @Override
    public String getParameter() {
        return PF;
    }

    @Override
    public List<String> getFeatures() {
        if (features == null) {
            init();
        }
        if (features.size() == 0) {
            features.add("");
        }
        return features;
    }

    @Override
    public String toString() {
        return "PF{" +
                "features=" + getFeatures() +
                ", fields=" + fields +
                ", weights=" + weights +
                '}';
    }
}
