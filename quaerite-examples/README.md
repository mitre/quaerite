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
This module offers examples to get your hands wet on example data
with example tasks.
This set of examples is built around the "TheMovieDB" (_tmdb_).
The inspiration for the use of this dataset comes from Doug Turnbull and John Berryman's book, [Relevant Search](https://www.manning.com/books/relevant-search)
and the "Think Like a Relevance Engineer" training offered by Doug and colleagues at [OpenSourceConnections (o19s)](https://opensourceconnections.com).
As you'll see, these examples rely on data and a Solr config kindly made available by Doug and _o19s_.

**Note: this module is under construction.  We still need to add the ```ground_truth.csv``` and ```experiments.json``` 
files... among other things.**

Prerequisites
------------
0. Install Java 8 and confirm that it is runnable from the commandline:
 ```$xslt java -version```
1. Download the OpenSourceConnection's _tmdb_ Solr index from [here](https://github.com/o19s/solr-tmdb/tree/master/solr_home)

2. Download and unpack Solr 7.7.1 from [here](http://www.apache.org/dyn/closer.lua/lucene/solr/7.7.1/solr-7.7.1.zip)   

3. Copy the directory ```demo_files/solr-7.x/tmbd``` to your Solr core directory

4. Start Solr:  ```./bin/solr start -f -s /path/to/solr/tmdb```

5. Download _tmdb.json_ from
   [AWS](https://s3.amazonaws.com/es-learn-to-rank.labs.o19s.com/tmdb.json) or [OpenSourceConnections](http://es-learn-to-rank.labs.o19s.com/tmdb.json)

6. Ingest the _tmdb_ data ```java -jar quaerite-examples.jar tmdb.json http://localhost:8983/solr/tmdb```

7. Navigate to [here](http://localhost:8983/solr) to confirm that _tmdb_ was loaded into Solr.

The stage is now set to start searching -- not for documents, but for relevance features.

Quaerite -- The Basics
---------------
Make sure that Solr is running as specified above.

1. Load the truth set into the local Quaerite database: ```java -jar quaerite.jar LoadJudgments -db my_db -f ground_truth.csv```
2. Load some initial experiments: ```java -jar quaerite.jar AddExperiments -db my_db -f experiments.json```
3. Run the experiments: ```java -jar quaerite.jar RunExperiments -db my_db -s http://localhost:8983/tmdb```
4. Once the experiments have completed, output the reports: ```java -jar quaerite.jar DumpResults -db my_db -d results```

You will find the standard reports in the ```reports/``` directory, including:
* Scores per query -- a score for each query for each experiment
* Scores aggregated by experiment -- a score for each experiment and query set
* A table showing the pairwise statistical significance tests (``paired t-test``) for each pair of experiments 

Quaerite -- Generating Experiments
--------------------------
It is a bit unwieldy to have to specify a definition for all of the experiments you might want to run.
You can specify features and ranges and have Quaerite create all the permutations of those experiments for you.

Be careful:
* Permutation explosion -- the number of experiments grows exponentially with each new parameter
* Overfitting -- this is something to be wary of throughout

1. Generate experiments from the example file: ```java -jar quaerite.jar GenerateExperiments -i experiment_parameters.json 
-o plethora_of_experiments.json```
2. Load the new experiments file: ```java -jar quaerite.jar AddExperiments -freshStart -db my_db -f plethora_of_experiments.json```

Then run the experiments and dump the results.

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

1. Load the truth set as above.
2. Run FindFeatures and specify which fields are your facetable fields with the ```-f``` flag:
```java -jar quaerite.jar FindFeatures -db my_db -s http://localhost:8983/solr/tmdb -f title,keywords,content```

Coming Soon -- Genetic Algorithms
---------------------------------
Rather than going through all of the permutatations with ```GenerateExperiments```, it would be useful to apply 
genetic algorithms to learn which combinations of features that lead to better results.
