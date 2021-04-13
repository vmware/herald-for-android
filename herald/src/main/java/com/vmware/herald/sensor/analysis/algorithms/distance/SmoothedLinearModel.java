//  Copyright 2021 Herald Project Contributors
//  SPDX-License-Identifier: Apache-2.0
//

package com.vmware.herald.sensor.analysis.algorithms.distance;

import com.vmware.herald.sensor.analysis.aggregates.Median;
import com.vmware.herald.sensor.analysis.sampling.Aggregate;
import com.vmware.herald.sensor.analysis.sampling.Sample;
import com.vmware.herald.sensor.data.ConcreteSensorLogger;
import com.vmware.herald.sensor.data.SensorLogger;
import com.vmware.herald.sensor.datatype.DoubleValue;

/// Distance model based on cable car experiment data collected on test rig 2
/// - Experiment data shows constructive and destructive wave interference have significant
///   impact on RSSI measurements at short range (0 - 3 metres).
/// - Interference stems from a combination of reflections in the environment (random) and
///   mixture of the three BLE advertising channels (more predictable). For reference,
///   channel 37 = 2.402 GHz, channel 38 = 2.426 GHz, and channel 39 = 2.480 GHz.
/// - Simulations have shown mixture of BLE channels play a significant part in RSSI variance
///   over minute distances, e.g. a change of 1cm can result in large RSSI change due to
///   subtle change in phases between the three channels. The impact of this is particularly
///   dominant at short range (0 - 2 metres).
/// - A range of modelling and smoothing algorithms were investigated to counter the impact
///   of reflections and channel mixing. Test results have shown the most widely applicable
///   method that is effective irrespective of environment is running a sliding window of
///   fixed duration (last 60 seconds) over the raw RSSI samples to calculate the median
///   RSSI value. Assuming the phones are not perfectly static (i.e. resting on a desk), the
///   small movements between two phones when carried on a person should be sufficient to
///   produce a wide range of interference patterns that on average offer a reasonably stable
///   estimate of the actual measurement.
/// - Experiment were conducted using different pairs of iOS and Android phones using
///   test rig 2, to capture raw RSSI measurements from 0 - 3.4 metres at 1cm resolution. On
///   average at least 60 RSSI measurements were taken at every 1cm. The data from all the
///   test runs were combined using dynamic time warping to align the RSSI data at each
///   distance. The result was then smoothed using median of a sliding window, then linear
///   regression was applied to estimate the intercept and coefficient for translating RSSI
///   to distance. Linear regression offered the following equation:
///      DistanceInMetres = -10.6522 + -0.181 x MedianOfRssi
///      Adjusted R-squared error = 0.8848cm
/// - Physical models for electromagnetic wave signal propagation are typically based on
///   log or squared distance, i.e. signal strength degrades logarithmically over distance.
///   The test rig 2 results confirm this, but also shows logarithmic degradation is only
///   obvious within the initial 0 - 20cm, then becomes linear. Given the intended purpose
///   of the distance metric (contact tracing) where risk score remains constant below 1m
///   and also the significant impact of interference within a short range, a linear model
///   avoids being skewed by the 0 - 20cm range, and offer simplicity for fitting the data
///   range of interest (1 - 8m).
public class SmoothedLinearModel<T extends DoubleValue> implements Aggregate<T> {
    private final SensorLogger logger = new ConcreteSensorLogger("Analysis", "SmoothedLinearModel");
    private int run = 1;
    private final Median<T> median = new Median<>();
    private final double intercept;
    private final double coefficient;

    public SmoothedLinearModel() {
        this(-10.6522, -0.181);
    }

    public SmoothedLinearModel(final double intercept, final double coefficient) {
        this.intercept = intercept;
        this.coefficient = coefficient;
    }

    @Override
    public int runs() {
        return 1;
    }

    @Override
    public void beginRun(int thisRun) {
        run = thisRun;
        median.beginRun(thisRun);
    }

    @Override
    public void map(Sample<T> value) {
        median.map(value);
   }

    @Override
    public Double reduce() {
        final Double medianOfRssi = medianOfRssi();
        if (medianOfRssi == null) {
            logger.debug("reduce, medianOfRssi is null");
            return null;
        }
        final Double distanceInMetres = intercept + coefficient * medianOfRssi;
        if (distanceInMetres <= 0) {
            logger.debug("reduce, out of range (medianOfRssi={},distanceInMetres={})", medianOfRssi, distanceInMetres);
            return null;
        }
        return distanceInMetres;
    }

    @Override
    public void reset() {
        median.reset();
    }

    public Double medianOfRssi() {
        return median.reduce();
    }

    public double intercept() {
        return intercept;
    }

    public double coefficient() {
        return coefficient;
    }

    public double maximumRssi() {
        return - intercept / coefficient;
    }
}