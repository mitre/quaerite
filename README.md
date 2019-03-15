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
Welcome to Quaerite
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

License (see also LICENSE.txt)
------------------------------

Copyright (c) 2019, The MITRE Corporation. All rights reserved.

Approved for Public Release; Distribution Unlimited. Case Number 18-3138.


Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements.  See the NOTICE file distributed with this work for additional information regarding copyright ownership.  The ASF licenses this file to You under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.  You may obtain a copy of the License at

<http://www.apache.org/licenses/LICENSE-2.0>

Getting Started
---------------
See the ```quaerite-examples``` module and its [README](https://github.com/mitre/quaerite/blob/master/quaerite-examples/README.md).

Road Map
----------
High priorities
* Bake crossfold validation into the Genetic Algorithm code
* Add other features (e.g. ```bq```, ```bf```) as needed
* Add a connector for Elasticsearch

Planned Releases
* 1.0.0-ALPHA before March 25, 2019
* 1.0.0-BETA before April 23, 2019