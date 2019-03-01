package org.mitre.quaerite.features;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TIE implements FeatureSet {
    private static final String TIE = "tie";

    public static TIE EMPTY = new TIE(Collections.singletonList(-1.0f));

    List<Float> ties = new ArrayList<>();

    public TIE(List<Float> ties) {
        this.ties = ties;
    }

    @Override
    public String getParameter() {
        return TIE;
    }

    @Override
    public List<String> getFeatures() {
        List<String> ret = new ArrayList<>();
        for (Float f : ties) {
            if (f >= 0.0) {
                ret.add(Float.toString(f));
            }
        }
        if (ret.size() == 0) {
            ret.add("");
        }
        return ret;
    }

}
