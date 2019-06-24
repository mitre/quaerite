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
package org.mitre.quaerite.cli;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

import org.apache.log4j.Logger;
import org.mitre.quaerite.core.Experiment;
import org.mitre.quaerite.core.ExperimentSet;
import org.mitre.quaerite.core.scorers.Scorer;
import org.mitre.quaerite.db.ExperimentDB;

public abstract class AbstractCLI {

    static Logger LOG = Logger.getLogger(AbstractCLI.class);

    static ExperimentSet addExperiments(ExperimentDB experimentDB,
                                        Path experimentsJson, boolean merge,
                                        boolean freshStart) throws SQLException, IOException {
        if (freshStart) {
            experimentDB.clearExperiments();
            experimentDB.clearScorers();
            experimentDB.clearScores();
            experimentDB.clearSearchResults();
        }
        ExperimentSet experiments = null;
        try (Reader reader = Files.newBufferedReader(experimentsJson, StandardCharsets.UTF_8)) {
            experiments = ExperimentSet.fromJson(reader);
        }

        for (Experiment experiment : experiments.getExperiments().values()) {
            experimentDB.addExperiment(experiment, merge);
        }

        List<Scorer> scorers = experiments.getScorers();
        if (scorers != null && scorers.size() > 0) {
            experimentDB.clearScorers();
            for (Scorer scorer : scorers) {
                experimentDB.addScorer(scorer);
            }
        }

        return experiments;
    }



}
