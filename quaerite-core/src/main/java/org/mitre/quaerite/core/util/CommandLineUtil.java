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
package org.mitre.quaerite.core.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.cli.CommandLine;

public class CommandLineUtil {
    public static int getInt(CommandLine commandLine, String opt, int dfault) {
        if (commandLine.hasOption(opt)) {
            return Integer.parseInt(commandLine.getOptionValue(opt));
        }
        return dfault;
    }

    public static float getFloat(CommandLine commandLine, String opt, float def) {
        if (commandLine.hasOption(opt)) {
            return Float.parseFloat(commandLine.getOptionValue(opt));
        }
        return def;
    }

    public static Path getPath(CommandLine commandLine, String opt, boolean mustExist) {
        if (! commandLine.hasOption(opt)) {
            return null;
        }
        Path p = Paths.get(commandLine.getOptionValue(opt));
        if (mustExist && !Files.exists(p)) {
            throw new IllegalArgumentException("File "+p+" must exist");
        }
        return p;
    }

    public static boolean getBoolean(CommandLine commandLine, String opt) {
        if (commandLine.hasOption(opt)) {
            return true;
        }
        return false;
    }

    public static String getString(CommandLine commandLine, String opt, String dfault) {
        if (commandLine.hasOption(opt)) {
            return commandLine.getOptionValue(opt);
        }
        return dfault;
    }

    public static long getLong(CommandLine commandLine, String opt, long dfault) {
        if (commandLine.hasOption(opt)) {
            return Long.parseLong(commandLine.getOptionValue(opt));
        }
        return dfault;
    }
}
