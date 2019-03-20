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

package org.mitre.quaerite.db;

import java.io.IOException;
import java.net.JarURLConnection;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.mitre.quaerite.core.JudgmentList;
import org.mitre.quaerite.core.Judgments;
import org.mitre.quaerite.core.util.MathUtil;

/**
 * Extends ExperimentDB to add more functionality for GA.
 *
 */
public class GADB extends ExperimentDB {

    public static GADB openAndDrop(Path dbDir) throws SQLException, IOException {
        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        return new GADB(DriverManager.getConnection(
                "jdbc:h2:" + dbDir.resolve("h2_database").toAbsolutePath()), true);
    }

    public static GADB open(Path dbDir) throws SQLException, IOException {
        try {
            Class.forName ("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        return new GADB(DriverManager.getConnection(
                "jdbc:h2:"+dbDir.resolve("h2_database").toAbsolutePath()), false);
    }

    private PreparedStatement getTestingStatement;

    private GADB(Connection connection, boolean dropAll) throws SQLException {
        super(connection, dropAll);
    }

    public void initTrainTest(JudgmentList test, JudgmentList all) throws SQLException {
        PreparedStatement insertTrainTest = initTrainTest();
        Set<String> added = new HashSet<>();
        int testFold = 0;
        int trainFold = 1;
        for (Judgments judgments : test.getJudgmentsList()) {
            insertTrainTest.clearParameters();
            insertTrainTest.setString(1, judgments.getQuery());
            insertTrainTest.setInt(2, testFold);
            insertTrainTest.execute();
            added.add(judgments.getQuery());
        }

        for (Judgments judgments : all.getJudgmentsList()) {
            if (added.contains(judgments.getQuery())) {
                continue;
            }
            insertTrainTest.clearParameters();
            insertTrainTest.setString(1, judgments.getQuery());
            insertTrainTest.setInt(2, trainFold);
            insertTrainTest.execute();
        }
    }
    public void initTrainTest(int nFolds) throws SQLException {
        PreparedStatement insertTrainTest = initTrainTest();
        JudgmentList judgmentList = getJudgments();

        List<Integer> foldIds = new ArrayList<>();
        while (foldIds.size() < judgmentList.getJudgmentsList().size()) {
            for (int i = 0; i < nFolds && foldIds.size() < judgmentList.getJudgmentsList().size(); i++) {
                foldIds.add(i);
            }
        }
        Collections.shuffle(foldIds, MathUtil.RANDOM);
        int i = 0;
        for (Judgments judgments : judgmentList.getJudgmentsList()) {
            insertTrainTest.clearParameters();
            insertTrainTest.setString(1, judgments.getQuery());
            insertTrainTest.setInt(2, foldIds.get(i++));
            insertTrainTest.execute();
        }

    }

    private PreparedStatement initTrainTest() throws SQLException {
        String sql = "drop table if exists train_test";
        executeSQL(connection, sql);

        sql = "create table train_test (query varchar(1024) primary key, fold integer);";
        executeSQL(connection, sql);

        getTestingStatement = connection.prepareStatement(
                "select query from train_test where fold = ?");

        sql = "insert into train_test values (?,?)";
        return connection.prepareStatement(sql);
    }

    public TrainTestJudmentListPair getTrainTestJudgmentsByFold(int fold) throws SQLException {
        Set<String> testNames = new HashSet<>();
        getTestingStatement.clearParameters();
        getTestingStatement.setInt(1, fold);
        try (ResultSet rs = getTestingStatement.executeQuery()) {
            while (rs.next()) {
                testNames.add(rs.getString(1));
            }
        }
        JudgmentList allJudgments = getJudgments();
        JudgmentList test = new JudgmentList();
        JudgmentList train = new JudgmentList();
        for (Judgments judgments : allJudgments.getJudgmentsList()) {
            if (testNames.contains(judgments.getQuery())) {
                test.addJudgments(judgments);
            } else {
                train.addJudgments(judgments);
            }
        }
        LOG.debug("train size: "+train.getJudgmentsList().size() +
                "; test size: "+test.getJudgmentsList().size());
        return new TrainTestJudmentListPair(train, test);
    }


}
