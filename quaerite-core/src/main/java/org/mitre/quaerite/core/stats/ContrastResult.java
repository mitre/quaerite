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
package org.mitre.quaerite.core.stats;


import java.util.Locale;

public class ContrastResult implements Comparable<ContrastResult> {
	String term = "";
	long targCount = 0;
	long targTotal = 0;
	long otherCount = 0;
	long otherTotal = 0;
	double contrastValue = 0;
	
	public ContrastResult(String term, long targCount, long targTotal,
                          long otherCount, long otherTotal, double contrastValue) {
		
		this.term = term;
		this.targCount = targCount;
		this.targTotal = targTotal;
		this.otherCount = otherCount;
		this.otherTotal = otherTotal;
		this.contrastValue = contrastValue;
	}

	public String getKey(){
		return getTerm();
	}
	public String getTerm() {
		return term;
	}
	public long getTargCount() {
		return targCount;
	}
	public long getTargTotal() {
		return targTotal;
	}
	public long getOtherCount() {
		return otherCount;
	}
	public long getOtherTotal() {
		return otherTotal;
	}
	public double getContrastValue() {
		return contrastValue;
	}

	@Override
	public String toString() {
		String targPercent = (targTotal == 0L) ? "" :

				String.format(Locale.US, "%.4f%%", ((double)targCount/(double)targTotal));
		String otherPercent = (otherTotal == 0L) ? "" :
				String.format(Locale.US, "%.4f%%", ((double)otherCount/(double)otherTotal), Locale.US);


		return "ContrastResult{" +
				"term='" + term + '\'' +
				", targCount=" + targCount +
				", targTotal=" + targTotal +
				", targPercent=" + targPercent+
				", otherCount=" + otherCount +
				", otherTotal=" + otherTotal +
				", otherPercent="+otherPercent+
				", contrastValue=" + contrastValue +
				'}';
	}

	@Override
	public int compareTo(ContrastResult o) {
		int c = Double.compare(o.contrastValue, this.contrastValue);
		if (c != 0) {
			return c;
		}
		c = Long.compare(o.targCount, this.targCount);
		if (c != 0) {
			return c;
		}
		c = Long.compare(o.otherCount, this.otherCount);
		if (c != 0) {
			return c;
		}
		return term.compareTo(o.term);
	}
}
