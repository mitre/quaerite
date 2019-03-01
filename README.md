Welcome to Quaerite
=================================================

Background and Goals
--------------------

This project includes tools to help evaluate relevance
ranking.  The current codebase is tightly coupled with Solr 4.x, 
but we plan to make this more easily extensible for more modern
versions of Solr as well as Elasticsearch.

This project is not intended to compete with existing relevance
evaluation tools; see for example: [1] and [2]. Rather, this was developed for use cases 
not currently covered by other open source software packages.
The author encourages collaboration among these projects.

NOTE: This project is under construction and is quite dynamic.  There will be breaking changes before the first major release.

While the name of this project may change in the future, we selected
_quaerite_ -- Latin imperative "seek", root of English "query" -- to
allude not only to the challenges of creating queries, but also
to the challenges of tuning search engines.  One may spend
a not insignificant amount of time tuning countless parameters.
In the end, we hope that _invenietis_ with slightly less effort
than without this project. For the pronunciation, 
[this link](https://forvo.com/word/quaerite_et_invenietis/).

License (see also LICENSE.txt)
------------------------------

Copyright (c) 2019, The MITRE Corporation. All rights reserved.

Approved for Public Release; Distribution Unlimited. Case Number 18-3138.


Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements.  See the NOTICE file distributed with this work for additional information regarding copyright ownership.  The ASF licenses this file to You under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.  You may obtain a copy of the License at

<http://www.apache.org/licenses/LICENSE-2.0>


References
----------
* [1] [Quepid](https://quepid.com/)

* [2] [Rated Ranking Evaluator](https://github.com/SeaseLtd/rated-ranking-evaluator/wiki/Maven-Plugin)
