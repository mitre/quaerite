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


import org.mitre.quaerite.tools.AddExperiments;
import org.mitre.quaerite.tools.DumpExperiments;
import org.mitre.quaerite.tools.LoadJudgments;
import org.mitre.quaerite.tools.RunExperiments;

public class QuaeriteCLI {

    public static void main(String[] args) throws Exception {
        String tool = args[0];
        String[] newArgs = new String[args.length-1];
        System.arraycopy(args,1, newArgs, 0, newArgs.length);
        if (tool.equals("LoadJudgments")) {
            LoadJudgments.main(newArgs);
        } else if (tool.equals("AddExperiments")) {
            AddExperiments.main(newArgs);
        } else if (tool.equals("DumpExperiments")) {
            DumpExperiments.main(newArgs);
        } else if (tool.equals("RunExperiments")) {
            //RunExperiments.main(newArgs);
        }else {
            System.err.println("I'm sorry, but I don't recognize \""+tool + "\" as a tool");
        }
    }
}
