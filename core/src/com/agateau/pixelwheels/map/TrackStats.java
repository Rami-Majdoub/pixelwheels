/*
 * Copyright 2018 Aurélien Gâteau <mail@agateau.com>
 *
 * This file is part of Pixel Wheels.
 *
 * Tiny Wheels is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.agateau.pixelwheels.map;

import java.util.ArrayList;

public class TrackStats {
    private static final int RECORD_COUNT = 3;

    private final GameStats.IO mIO;
    private final ArrayList<TrackResult> mLapRecords;
    private final ArrayList<TrackResult> mTotalRecords;

    public enum ResultType {
        LAP,
        TOTAL
    }

    TrackStats(GameStats.IO io) {
        mIO = io;
        mLapRecords = new ArrayList<TrackResult>();
        mTotalRecords = new ArrayList<TrackResult>();
    }

    public ArrayList<TrackResult> get(ResultType resultType) {
        return resultType == ResultType.LAP ? mLapRecords : mTotalRecords;
    }

    public int addResult(ResultType resultType, TrackResult result) {
        int rank = addResult(get(resultType), result);
        if (rank != -1) {
            mIO.save();
        }
        return rank;
    }

    private static int addResult(ArrayList<TrackResult> results, TrackResult result) {
        // Insert result if it is better than an existing one
        for (int idx = 0; idx < results.size(); ++idx) {
            if (result.value < results.get(idx).value) {
                results.add(idx, result);
                if (results.size() > RECORD_COUNT) {
                    results.remove(RECORD_COUNT);
                }
                return idx;
            }
        }
        // If result is not better than existing ones but there is room at the end, append it
        if (results.size() < RECORD_COUNT) {
            results.add(result);
            return results.size() - 1;
        }
        return -1;
    }
}
