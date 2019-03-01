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

import org.junit.jupiter.api.Test;
import org.mitre.quaerite.stats.YatesChi;

public class YatesChiTest {

    @Test
    public void testBasic() {
        YatesChi chi = new YatesChi();
        double fC = 1;
        double fT = 1138;
        double bC = 91;
        double bT = 124456;
        double a = 0;
        double b = fT-fC;
        double c = bC;
        double d = bT-bC;
        System.out.println(chi.calculateValue(a, b, c, d));
        //32 .15
        //91 .12
    }
}
