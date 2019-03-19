<!--
  Licensed to the Apache Software Foundation (ASF) under one
  or more contributor license agreements.  See the NOTICE file
  distributed with this work for additional information
  regarding copyright ownership.  The ASF licenses this file
  to you under the Apache License, Version 2.0 (the
  "License"); you may not use this file except in compliance
  with the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing,
  software distributed under the License is distributed on an
  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  KIND, either express or implied.  See the License for the
  specific language governing permissions and limitations
  under the License.
-->
Quaerite Examples
===========================
This module offers examples to get your hands dirty on example data
with example tasks.
This set of examples is built around the "TheMovieDB" (_tmdb_).
The inspiration for the use of this dataset comes from Doug Turnbull and John Berryman's book, [Relevant Search](https://www.manning.com/books/relevant-search)
and the "Think Like a Relevance Engineer" training offered by Doug and colleagues at [OpenSourceConnections (o19s)](https://opensourceconnections.com).
As you'll see, these examples rely on data kindly made 
available by Doug and _o19s_ -- the ```tmdb.json``` file is hosted by _o19s_, and the ```movie_judgments.csv``` 
file is a reformatting of _o19s_'s [judgment-lists](https://github.com/o19s/solr-tmdb/blob/master/ltr/judgment-lists.html).

Prerequisites
------------
0. Install Java 8 and confirm that it is runnable from the commandline:
 ```java -version```
1. Until there is an official release of ```quaerite```, you'll need to build the project to build the jars:     ```mvn clean install```

2. Download the _tmdb_ Solr index config [Solr 7.x-8.x](https://github.com/mitre/quaerite/blob/master/quaerite-examples/example_files/solr-7And8.x.zip)
   or [Solr 4.x](https://github.com/mitre/quaerite/blob/master/quaerite-examples/example_files/solr-4.x.zip)

3. Download and unpack Solr 8.0.0 from [here](http://www.apache.org/dyn/closer.lua/lucene/solr/8.0.0/solr-8.0.0.zip).

4. Unzip the index config from step 1 to your Solr core directory, e.g. ```solr-8.0.0/solr/server/tmdb```

5. Start Solr:  ```./bin/solr start -f -s /path/to/solr/tmdb```

6. Download _tmdb.json_ from
   [OpenSourceConnections via AWS](http://o19s-public-datasets.s3.amazonaws.com/tmdb.json).

7. Ingest the _tmdb_ data ```java -jar quaerite-examples-1.0.0-SNAPSHOT.jar tmdb.json http://localhost:8983/solr/tmdb```

8. Navigate to [here](http://localhost:8983/solr/#/tmdb) to confirm that _tmdb_ was loaded into Solr.

    
The stage is now set to start searching -- not for documents, but for relevance features.

Quaerite -- The Basics -- Running Experiments (```RunExperiments```)
---------------

You can find the files (such as ```movie_judgments.csv``` and 
```experiments.json```) in [this directory](https://github.com/mitre/quaerite/tree/master/quaerite-examples/example_files).


Run some experiments: ```java -jar quaerite-cli-1.0.0-SNAPSHOT.jar RunExperiments -db my_db -j movie_judgments.csv -e experiments.json```

You will find the standard reports in the ```reports/``` directory, including:
* Scores per query -- a score for each query for each experiment
* Scores aggregated by experiment -- a score for each experiment and query set
* A table showing the pairwise statistical significance tests (``paired t-test``) for each pair of experiments
  as scored by any ScoreCollector with a ```exportPMatrix=true```.

From these reports, we can quickly see that the `title` field yields the best results for this ground truth judgment set.
When we look at the p-value matrix (`sig_diffs_ndcg_10.csv`), we can see that `title` is significantly better than  
`overview`, and `overview` is significantly better than `people`.

Quaerite -- Generating Experiments (```GenerateExperiments```)
--------------------------
It is a bit unwieldy to have to specify a definition for all of the experiments you might want to run.
You can specify features and ranges and have ```quaerite``` create all the permutations of those experiments for you.

Be careful:
* Permutation explosion -- the number of experiments grows factorially with each new parameter
* Overfitting -- this is something to be wary of throughout

1. Generate experiments from the `experiment_features_1.json` file, which generates one experiment per field: ```java -jar quaerite-cli-1.0.0-SNAPSHOT.jar GenerateExperiments -f experiment_features_1.json -e experiments_1.json```
2. Generate experiments from the `experiment_features_2.json` file, which generates experiments with up to two fields: ```java -jar quaerite-cli-1.0.0-SNAPSHOT.jar GenerateExperiments -f experiment_features_2.json -e experiments_2.json```
3. Now, let's say you want to test a wider range of fields that include different analyzer chains for the 
three fields used so far (`tb_*` and `tss_*`).  Further, you'd like to experiment with different weight settings, e.g. `[0.0, 1.0, 5.0, 10.0]`, but
you'd still like to see which single field or pair of fields yields the best results on the ground truth set...
Generate experiments from the `experiment_features_3.json` file: ```java -jar quaerite-cli-1.0.0-SNAPSHOT.jar GenerateExperiments -f experiment_features_3.json -e experiments_3.json```
4. Finally, you'd like to remove the maximum of two fields per experiment, and you'd like to add in various `tie` values (e.g. `0.0, 0.1, 0.3`),
Generate experiments from the `experiment_features_4.json` file: ```java -jar quaerite-cli-1.0.0-SNAPSHOT.jar GenerateExperiments -f experiment_features_4.json -e experiments_4.json```

You can now run the experiments in any one of these experiment files: ```java -jar quaerite-cli-1.0.0-SNAPSHOT.jar RunExperiments -db my_db -j movie_judgments.csv -e experiments_1.json```

Each time you run the experiments, the results in the ```results/``` directory will be overwritten.

There are 350 experiments generated by `experiment_features_3.json`, and 3,041 experiments 
generated by `experiment_features_4.json`.

So, one option, if you wanted to be thorough, would be to start running the ~3,000 experiments generated from `experiments_4.json`
and get some coffee because those ~3,000 experiments will take some time.  

The other option, rather than running all of these experiments, 
would be to give the genetic algorithm a try (see the next section:```RunGA```).

Genetic Algorithms (GA) (```RunGA```)
---------------------------------
Rather than going through all of the permutatations with ```GenerateExperiments```, it would be useful to apply 
genetic algorithms to learn which combinations of features lead to better results.

1. Run the genetic algorithm from the features specification file with the original experiments as the seed: 
```java -jar quaerite-cli-1.0.0-SNAPSHOT.jar RunGA -db my_db -j movie_judgments.csv -f experiment_features_2.json -sc ndcg_10_mean -e experiments.json```
2. Or run the genetic algorithm from the features specification file with the a random seed: 
```java -jar quaerite-cli-1.0.0-SNAPSHOT.jar RunGA -db my_db -j movie_judgments.csv -f experiment_features_2.json -sc ndcg_10_mean```

```RunGA``` shows the top 10 experiments from each generation, and it writes the `.json` experiment files 
(by default) to the ```ga_experiments``` directory.  ```RunGA``` does not currently automatically write reports on the results.

On this data set, with the available features, there is not much improvement over random seeding from option 2.  However, option 1 should show how the GA is finding better parameter settings over the initial experiment with each generation.  
Note, that the next generation is not guaranteed to be better than the last -- at least with the current settings.



Quaerite -- Finding Features
-----------------------------
Elasticsearch made popular the notion of "SignificantTerms" -- that is, given a query
what facets are statistically interestingly more likely to appear than they would be 
in the overall index.

If you are coming to a new use case and/or a new index, it can be helpful
to understand properties of the ground truth set, especially a ground truth 
set that represents the most common queries.

For example, on one collection, we noticed that, based on the ground truth set, searchers
were most commonly looking for the "sections" type pages, and the most popular queries 
did not appear to be as interested in the "PDF library" or other document sets.

What you do with this information with regard to relevance tuning depends on many factors,
but it can be helpful at least to understand statistical patterns that
your truth set may reveal.

1. Run FindFeatures and specify which fields are your facetable fields with the ```-f``` flag:
```java -jar quaerite-cli-1.0.0-SNAPSHOT.jar FindFeatures -db my_db -j movie_judgments.csv -s http://localhost:8983/solr/tmdb -f genres_facet,original_language_facet,production_companies_facet```

For each facet, the results are sorted by the descending order of contrast value. 
For example:
```
production_companies_facet:
   	facet_value=dc comics
   		targCount=16
   		targTotal=1481
   		targPercent=1.08%
   		backgroundCount=23
   		backgroundTotal=26365
   		backgroundPercent=0.09%
   		contrastValue=98.884
   	facet_value=lin pictures
   		targCount=3
   		targTotal=1481
   		targPercent=0.20%
   		backgroundCount=0
   		backgroundTotal=26365
   		backgroundPercent=0.00%
   		contrastValue=53.412
```

Slightly more than one percent (1.08%) of the production companies in the truth set are ```dc_comics```, 
however, in the rest of ```tmdb```, only 23 movies in the rest of ```tmdb``` (0.20%) are ```dc_comics```.

Note, that the contrastive value is irrespective of the directionality.  
This shows that the truth set has _fewer_ ```drama``` movies than you'd expect.

```
genres_facet:
...
	facet_value=drama
		targCount=402
		targTotal=1481
		targPercent=27.14%
		backgroundCount=8914
		backgroundTotal=26365
		backgroundPercent=33.81%
		contrastValue=27.989
...
```

In this judgment set, the contrastive values are not exceedingly strong.  
In practice, however, this technique has revealed some very important patterns that could help with either:

1. pointing out areas for improvement in supplementing the truth set
2. tuning the boost weights based on popular categories

