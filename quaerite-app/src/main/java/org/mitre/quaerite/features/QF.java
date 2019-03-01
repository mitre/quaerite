package org.mitre.quaerite.features;

import java.util.ArrayList;
import java.util.List;

public class QF implements FeatureSet {
    private static final String QF = "qf";

    List<String> features;
    List<String> fields = new ArrayList<>();
    List<Float> weights = new ArrayList<>();

    public QF(List<String> fields, List<Float> weights) {
        this.fields = fields;
        this.weights = weights;
        init();
    }

    private void init() {
        features = new ArrayList<>();
        features.addAll(fields);
        for (int i = 0; i < fields.size()-1; i++) {
            for (int j = i+1; j < fields.size(); j++) {
                String combo = fields.get(i) + "," + fields.get(j);
                features.add(combo);
            }
        }

//        StringBuilder sb = new StringBuilder();
  //      recurse(0, fields, weights, sb);
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
        return QF;
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
        return "QF{" +
                "features=" + getFeatures() +
                ", fields=" + fields +
                ", weights=" + weights +
                '}';
    }
}
