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
package org.mitre.quaerite.core;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.lang3.tuple.Pair;
import org.mitre.quaerite.core.features.CustomHandler;
import org.mitre.quaerite.core.features.StringFeature;
import org.mitre.quaerite.core.features.URL;
import org.mitre.quaerite.core.features.factories.CustomHandlerFactory;
import org.mitre.quaerite.core.features.factories.FeatureFactories;
import org.mitre.quaerite.core.features.factories.FeatureFactory;
import org.mitre.quaerite.core.features.factories.QueryListFactory;
import org.mitre.quaerite.core.features.factories.StringFeatureFactory;
import org.mitre.quaerite.core.queries.LuceneQuery;
import org.mitre.quaerite.core.queries.Query;
import org.mitre.quaerite.core.scoreaggregators.ScoreAggregator;
import org.mitre.quaerite.core.scoreaggregators.ScoreAggregatorListSerializer;
import org.mitre.quaerite.core.serializers.FeatureFactorySerializer;
import org.mitre.quaerite.core.util.MathUtil;

public class ExperimentFactory {

    public static  final String SEARCH_SERVER_URLS = "urls";
    public static  final String QUERIES = "queries";

    private GAConfig gaConfig = new GAConfig();

    Map<String, List<String>> fixedParameters;
    List<ScoreAggregator> scoreAggregators;
    FeatureFactories featureFactories;

    public static ExperimentFactory fromJson(Reader reader) {
        Gson gson = new GsonBuilder().setPrettyPrinting()
                .registerTypeHierarchyAdapter(ScoreAggregator.class, new ScoreAggregatorListSerializer.ScoreAggregatorSerializer())
                .registerTypeAdapter(FeatureFactories.class, new FeatureFactorySerializer())
                .create();
        return gson.fromJson(reader, ExperimentFactory.class);
    }
    private transient ScoreAggregator trainScoreAggregator;
    private transient ScoreAggregator testScoreAggregator;

    public List<ScoreAggregator> getScoreAggregators() {
        return scoreAggregators;
    }

    @Override
    public String toString() {
        return "ExperimentFactory{" +
                "gaConfig=" + gaConfig +
                ", fixedParameters=" + fixedParameters +
                ", scoreAggregators=" + scoreAggregators +
                ", featureFactories=" + featureFactories +
                ", trainScoreAggregator=" + trainScoreAggregator +
                ", testScoreAggregator=" + testScoreAggregator +
                '}';
    }

    public FeatureFactories getFeatureFactories() {
        return featureFactories;
    }

    public ScoreAggregator getTrainScoreAggregator() {
        if (trainScoreAggregator == null) {
            if (scoreAggregators.size() == 0) {
                trainScoreAggregator = scoreAggregators.get(0);
            } else {
                boolean found = false;
                for (ScoreAggregator scoreAggregator : scoreAggregators) {
                    if (scoreAggregator.getUseForTrain()) {
                        if (found) {
                            throw new IllegalArgumentException("Can't have more than one train score aggregator!");
                        }
                        trainScoreAggregator = scoreAggregator;
                        found = true;
                    }
                }
            }
        }
        return trainScoreAggregator;
    }

    public ScoreAggregator getTestScoreAggregator() {
        if (testScoreAggregator == null) {
            if (scoreAggregators.size() == 1) {
                testScoreAggregator = scoreAggregators.get(0);
            } else {
                boolean found = false;
                for (ScoreAggregator scoreAggregator : scoreAggregators) {
                    if (scoreAggregator.getUseForTest()) {
                        if (found) {
                            throw new IllegalArgumentException("Can't have more than one test score aggregator!");
                        }
                        testScoreAggregator = scoreAggregator;
                        found = true;
                    }
                }
            }
        }
        return testScoreAggregator;
    }

    public GAConfig getGAConfig() {
        return gaConfig;
    }

    public Map<String, List<String>> getFixedParameters() {
        return fixedParameters;
    }

    public Experiment generateRandomExperiment(String name) {
        FeatureFactory urlFactory = featureFactories.get(SEARCH_SERVER_URLS);
        String searchUrl = urlFactory.random().toString();
        CustomHandler customHandler = null;
        CustomHandlerFactory customHandlerfactory = (CustomHandlerFactory)featureFactories.get(CustomHandlerFactory.NAME);
        if (customHandlerfactory != null) {
            customHandler = customHandlerfactory.random();
        }
        QueryListFactory queryListFactory = (QueryListFactory)featureFactories.get(QUERIES);
        Experiment rand = new Experiment(name, searchUrl, customHandler, queryListFactory.random());
        addFilterQueries(rand);
        return rand;
    }

    private void addFilterQueries(Experiment experiment) {
        if (fixedParameters.containsKey("filterQueries")) {
            List<Query> filterQueries = new ArrayList<>();
            for (String q : fixedParameters.get("filterQueries")) {
                filterQueries.add(new LuceneQuery("", q));
            }
            experiment.addFilterQueries(filterQueries);
        }
    }

    public List<Experiment> permute(int maxExperiments) {
        List<Experiment> experiments = new ArrayList<>();
        for (URL url : ((StringFeatureFactory<URL>)(featureFactories.get(SEARCH_SERVER_URLS))).permute(maxExperiments)) {
            for (Query q : ((QueryListFactory)(featureFactories.get(QueryListFactory.NAME))).permute(maxExperiments)) {
                for (CustomHandler handler : permuteHandlers(maxExperiments)) {
                    if (experiments.size() >= maxExperiments) {
                        return experiments;
                    }
                    Experiment ex = new Experiment("permute_"+experiments.size(), url.toString(), handler, q);
                    addFilterQueries(ex);
                    experiments.add(ex);
                }
            }
        }
        //TODO stub
        throw new IllegalArgumentException("stub -- must develop");
    }

    private List<CustomHandler> permuteHandlers(int maxSize) {
        if (featureFactories.get(CustomHandlerFactory.NAME) == null) {
            List<CustomHandler> customHandlers = new ArrayList<>();
            customHandlers.add(null);
            return customHandlers;
        }

        return ((CustomHandlerFactory)featureFactories.get(CustomHandlerFactory.NAME)).permute(maxSize);
    }

    public Pair<Experiment, Experiment> crossover(Experiment parentA, Experiment parentB) {
        StringFeatureFactory featureFactory = (StringFeatureFactory)featureFactories.get(SEARCH_SERVER_URLS);
        Pair<URL, URL> urls = featureFactory.crossover(
                new URL(parentA.getSearchServerUrl()), new URL(parentB.getSearchServerUrl()));
        Pair<CustomHandler, CustomHandler> customHandlers = Pair.of(null, null);
        if (featureFactories.get(CustomHandlerFactory.NAME) != null) {
                    customHandlers = featureFactories.get(CustomHandlerFactory.NAME).crossover(parentA.getCustomHandler(),
                            parentB.getCustomHandler());
        }

        QueryListFactory queryListFactory = (QueryListFactory)featureFactories.get(QueryListFactory.NAME);

        Pair<Query, Query> queries = queryListFactory.crossover(parentA.getQuery(), parentB.getQuery());

        URL urlA = (MathUtil.RANDOM.nextFloat() <= 0.5) ? urls.getLeft() : urls.getRight();
        CustomHandler customHandlerA = (MathUtil.RANDOM.nextFloat() <= 0.5) ? customHandlers.getLeft() : customHandlers.getRight();
        Query queryA = (MathUtil.RANDOM.nextFloat() <= 0.5) ? queries.getLeft() : queries.getRight();
        Experiment childA = new Experiment("childA", urlA.toString(), customHandlerA, queryA);

        URL urlB = (MathUtil.RANDOM.nextFloat() <= 0.5) ? urls.getLeft() : urls.getRight();
        CustomHandler customHandlerB = (MathUtil.RANDOM.nextFloat() <= 0.5) ? customHandlers.getLeft() : customHandlers.getRight();
        Query queryB = (MathUtil.RANDOM.nextFloat() <= 0.5) ? queries.getLeft() : queries.getRight();
        Experiment childB = new Experiment("childB", urlB.toString(), customHandlerB, queryB);
        addFilterQueries(childA);
        addFilterQueries(childB);
        return Pair.of(childA, childB);

    }

    public Experiment mutate(Experiment parent, float mutationProbability, float mutationAmplitude) {
        Experiment mutated = parent.deepCopy();
        if (MathUtil.RANDOM.nextFloat() < mutationProbability) {
            FeatureFactory featureFactory = featureFactories.get(SEARCH_SERVER_URLS);
            String serverUrl = featureFactory.mutate(
                    new URL(mutated.getSearchServerUrl()), mutationProbability, mutationAmplitude).toString();
            mutated.setSearchServerUrl(serverUrl);
        }

        if (MathUtil.RANDOM.nextFloat() < mutationProbability && featureFactories.get(CustomHandlerFactory.NAME) != null) {
            CustomHandler customHandler = mutated.getCustomHandler();
            CustomHandler mutatedHandler =
                    ((CustomHandlerFactory)featureFactories.get(CustomHandlerFactory.NAME)).mutate(customHandler, mutationProbability, mutationAmplitude);
            mutated.setCustomHandler(mutatedHandler);
        }

        if (MathUtil.RANDOM.nextFloat() < mutationProbability) {
            Query q = mutated.getQuery();
            Query mutatedQuery =
                    ((QueryListFactory)featureFactories.get(QueryListFactory.NAME)).mutate(q, mutationProbability, mutationAmplitude);
            mutated.setQuery(mutatedQuery);
        }
        addFilterQueries(mutated);
        return mutated;
    }
}
