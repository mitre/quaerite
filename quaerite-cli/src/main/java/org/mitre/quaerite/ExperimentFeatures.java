/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
    List<String> searchServerUrls;
    List<String> customHandlers;
    //TODO: make these more general
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

    public List<String> getSearchServerUrls() {
        return searchServerUrls;
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
                ", searchServerUrls=" + searchServerUrls +
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
