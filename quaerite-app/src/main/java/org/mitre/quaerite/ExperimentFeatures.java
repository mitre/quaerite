package org.mitre.quaerite;

import java.io.Reader;
import java.util.List;

import org.mitre.quaerite.features.FeatureSet;
import org.mitre.quaerite.features.PF;
import org.mitre.quaerite.features.PF2;
import org.mitre.quaerite.features.PF3;
import org.mitre.quaerite.features.QF;
import org.mitre.quaerite.features.TIE;
import org.mitre.quaerite.scorecollectors.ScoreCollector;
import org.mitre.quaerite.scorecollectors.ScoreCollectorListSerializer;

public class ExperimentFeatures {

    List<ScoreCollector> scoreCollectors;
    List<String> solrUrls;
    List<String> customHandlers;
    QF qf;
    PF pf;
    PF2 pf2;
    PF3 pf3;
    TIE tie;

    public static ExperimentFeatures fromJson(Reader reader) {
        return ScoreCollectorListSerializer.GSON.fromJson(reader, ExperimentFeatures.class);
    }

    public List<ScoreCollector> getScoreCollectors() {
        return scoreCollectors;
    }

    public List<String> getSolrUrls() {
        return solrUrls;
    }

    public List<String> getCustomHandlers() {
        return customHandlers;
    }

    public QF getQf() {
        return qf;
    }

    @Override
    public String toString() {
        return "ExperimentFeatures{" +
                "scoreCollectors=" + scoreCollectors +
                ", solrUrls=" + solrUrls +
                ", customHandlers=" + customHandlers +
                ", qf=" + qf +
                '}';
    }

    public FeatureSet getPF() {
        return  (pf != null) ? pf : PF.EMPTY;
    }

    public FeatureSet getPF2() {
        return (pf2 != null) ? pf2 : PF2.EMPTY;
    }

    public FeatureSet getPF3() {
        return (pf3 != null) ? pf3 : PF3.EMPTY;
    }

    public FeatureSet getTie() {
        return (tie != null) ? tie : TIE.EMPTY;
    }
}
