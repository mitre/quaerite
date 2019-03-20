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
Welcome to _Quaerite_
=================================================

Background and Goals
--------------------

This project includes tools to help evaluate relevance
ranking.  This code has been tested with Solr 4.x, 7.x and 8.x.
We plan to add a connector for Elasticsearch. 

This project is not intended to compete with existing relevance
evaluation tools, such as [Splainer](http://splainer.io/),
[Quepid](https://quepid.com/), [Rated Ranking Evaluator](https://github.com/SeaseLtd/rated-ranking-evaluator/wiki/Maven-Plugin),
or [Luigi's Box](https://www.luigisbox.com/).
Rather, this project was developed for use cases not currently 
covered by open source software packages. The author encourages 
collaboration among these projects.

**NOTE: This project is under construction and is quite dynamic.  
There will be breaking changes before the first major release.**

While the name of this project may change in the future, we selected
_quaerite_ -- Latin imperative "seek", root of English "query" -- to
allude not only to the challenges of creating queries, but also
to the challenges of tuning search engines.  One may spend
a not insignificant amount of time tuning countless parameters.
In the end, we hope that _invenietis_ with slightly less effort
than without this project. For the pronunciation, see
[this link](https://forvo.com/word/quaerite_et_invenietis/).

Similarities and Differences between the Genetic Algorithm (GA) in _Quaerite_ and Learning to Rank
------------------------------------------ 
In the research literature, the application of a GA or Genetic Programming (GP) is one method for learning to rank (see, e.g. 
[Andrew Trotman](https://www.academia.edu/282562/Learning_to_Rank) on GP).

However, for integrators and developers who work in the Lucene ecosystem, "Learning to Rank" (LTR) connotes
a specific methodology/module initially added to Apache Solr by [Bloomberg](https://www.techatbloomberg.com/blog/bloomberg-integrated-learning-rank-apache-solr/) 
and then offered as a plugin for ElasticSearch by 
[Doug Turnbull and colleagues at OpenSource Connections, Wikimedia Foundation and Snagajob Engineering](https://elasticsearch-learning-to-rank.readthedocs.io/en/latest/).
In the following, I use LTR to refer to this Lucene-ecosystem-specific module and methodology.  

In _no_ way do I see this implementation of GA as a competitor to LTR; rather, it is another tool that 
might help complement LTR and/or other tuning methodologies.

### Similarities
* All of the basic requirements for quality search must first be met -- analyzer chains must be well designed for the data, 
    the underlying data in the index should be accurate, well organized and well curated 
* There must be sufficient, high quality, accurate and representative ground truth judgments for training and testing
* Machine learning can only do so much -- further tuning and/or adding new methods of enrichment may be required

### Differences
* In practice, LTR is designed to perform more costly calculations as a re-ranking step...that is, after the search 
engine has returned the best `n` documents, LTR is typically applied to carry out more costly calculations on 
this smaller subset of documents to re-rank the results based on the models built offline.  The goal of 
this implementation of GA (and the other tools in _Quaerite_) is to help tune the parameters used in the initial 
search system's ranking, _NOT_ as part of a secondary reranking.
* Bloomberg, OpenSource Connections, Wikimedia and Snagajob have spent quite a bit of time and effort 
developing and integrating these modules to make them easy to use.  This toolkit has been developed with 
far fewer resources for use initially by one relevance engineer...there are areas for improvement.


License (see also LICENSE.txt)
------------------------------

Copyright (c) 2019, The MITRE Corporation. All rights reserved.

Approved for Public Release; Distribution Unlimited. Case Number 18-3138.


Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements.  See the NOTICE file distributed with this work for additional information regarding copyright ownership.  The ASF licenses this file to You under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.  You may obtain a copy of the License at

<http://www.apache.org/licenses/LICENSE-2.0>

Current Status
----------------
As of this writing, `Quaerite` allows for experimentation with the following parameters:
`bq`, `qf`, `pf`, `pf2`, `pf3`, `solr url` (so that you can run experiments against 
different cores and/or different versions of Solr),
`customHandler` (so that you can compare different customized handlers), `tie`.

Before the `BETA` release, we'll add:  `q.op`, `mm`, `bf` and a more parameterizable version of `bq`.

Getting Started
---------------
See the ```quaerite-examples``` module and its [README](https://github.com/mitre/quaerite/blob/master/quaerite-examples/README.md).


Road Map
----------
High priorities
* Add other features (e.g. ```bq```, ```bf```) as needed
* Add a connector for Elasticsearch
* See the issues on [github](https://github.com/mitre/quaerite/issues)

Planned Releases
* 1.0.0-ALPHA before March 25, 2019
* 1.0.0-BETA before April 23, 2019