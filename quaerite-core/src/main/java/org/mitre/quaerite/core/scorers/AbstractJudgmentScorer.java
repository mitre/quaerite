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
 *
 */
package org.mitre.quaerite.core.scorers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.mitre.quaerite.core.Judgments;
import org.mitre.quaerite.core.SearchResultSet;

public abstract class AbstractJudgmentScorer
        extends DistributionalScoreAggregator implements JudgmentScorer {

    static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private boolean useForTrain = false;
    private boolean useForTest = false;
    private boolean exportPMatrix = false;


    public AbstractJudgmentScorer(String name, int atN) {
        super(name, atN);
    }

    public abstract double score(Judgments judgments,
                                 SearchResultSet searchResultSet);




    public boolean getUseForTrain() {
        return useForTrain;
    }

    public void setUseForTrain() {
        this.useForTrain = true;
    }

    public boolean getUseForTest() {
        return useForTest;
    }

    public void setUseForTest() {
        this.useForTest = true;
    }

    public boolean getExportPMatrix() {
        return exportPMatrix;
    }

    public void setExportPMatrix() {
        this.exportPMatrix = true;
    }
}
