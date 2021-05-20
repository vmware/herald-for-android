//  Copyright 2020-2021 Herald Project Contributors
//  SPDX-License-Identifier: Apache-2.0
//

package io.heraldprox.herald.sensor.datatype;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import io.heraldprox.herald.sensor.datatype.random.BlockingSecureRandom;
import io.heraldprox.herald.sensor.datatype.random.BlockingSecureRandomNIST;
import io.heraldprox.herald.sensor.datatype.random.BlockingSecureRandomSingleton;
import io.heraldprox.herald.sensor.datatype.random.NonBlockingCSPRNG;
import io.heraldprox.herald.sensor.datatype.random.NonBlockingPRNG;
import io.heraldprox.herald.sensor.datatype.random.NonBlockingSecureRandom;
import io.heraldprox.herald.sensor.datatype.random.RandomSource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class RandomSourceTests {

    /**
     * Get a list of all random sources for testing.
     * @return
     */
    private final List<RandomSource> randomSources() {
        final List<RandomSource> randomSources = Arrays.asList(
                new NonBlockingPRNG(),
                new NonBlockingCSPRNG(),
                new NonBlockingSecureRandom(),
                new BlockingSecureRandom(),
                new BlockingSecureRandomSingleton(),
                new BlockingSecureRandomNIST());
        return randomSources;
    }

    // MARK: - Basic tests to ensure the random sources are not obviously
    //         generating similar values most of the time due to coding errors,
    //         hence the tolerance for duplicates is high.

    @Test
    public void testNextInt() {
        for (RandomSource randomSource : randomSources()) {
            int duplicates = 0;
            int lastValue = randomSource.nextInt();
            for (int i=0; i<1000; i++) {
                final int value = randomSource.nextInt();
                if (value == lastValue) {
                    duplicates++;
                }
                lastValue = value;
            }
            // Duplicates may occur, but should be unlikely
            assertTrue(duplicates < 900);
        }
    }

    @Test
    public void testNextLong() {
        for (RandomSource randomSource : randomSources()) {
            int duplicates = 0;
            long lastValue = randomSource.nextLong();
            for (int i=0; i<1000; i++) {
                final long value = randomSource.nextLong();
                if (value == lastValue) {
                    duplicates++;
                }
                lastValue = value;
            }
            // Duplicates may occur, but should be unlikely
            assertTrue(duplicates < 900);
        }
    }

    @Test
    public void testAddEntropy() {
        for (RandomSource randomSource : randomSources()) {
            int duplicates = 0;
            int lastValue = randomSource.nextInt();
            for (int i=-1000; i<1000; i++) {
                randomSource.addEntropy(i);
                randomSource.addEntropy(Integer.toString(i));
                final int value = randomSource.nextInt();
                if (value == lastValue) {
                    duplicates++;
                }
                lastValue = value;
            }
            // Duplicates may occur, but should be unlikely
            assertTrue(duplicates < 900);
        }
    }

    // MARK: - Test output values are likely to be uniformly distributed

    /**
     * Test distribution of random values are uniformly distributed
     */
    @Test
    public void testDistribution() {
        // Using 200,000 sample to ensure test completes within reasonable time
        final long samples = 200000;
        // Setting tolerance for mean absolute error to be 10% given small sample
        final double threshold = 0.10;
        for (RandomSource randomSource : randomSources()) {
             // Test distribution of sequence
            final long[] histogramOfSequence = histogramOfSequence(randomSource, samples);
            final double sequenceError = isUniformDistribution(histogramOfSequence);
            // Test distribution of first value
            final long[] histogramOfValue = histogramOfValue(randomSource, samples);
            final double valueError = isUniformDistribution(histogramOfValue);
            System.out.println("testDistribution(randomSource="+randomSource.getClass().getSimpleName()+",samples="+samples+",sequenceError="+sequenceError+",valueError="+valueError+")");
            assertTrue(sequenceError < threshold);
            assertTrue(valueError < threshold);
        }
    }

    @Test
    public void testNextLongPerformance() {
        // Using 200,000 sample to ensure test completes within reasonable time
        final long samples = 200000;
        for (RandomSource randomSource : randomSources()) {
            nextLongPerformance(randomSource, samples);
        }
    }


    // MARK: - Supporting test functions

    /**
     * Compute histogram of first N bytes from a random source to test if the sequence is
     * likely to be uniformly distributed.
     * @param randomSource
     * @param samples Number of samples, in multiples of 256 bytes
     * @return Histogram of random values for assessing distribution
     */
    private final static long[] histogramOfSequence(RandomSource randomSource, long samples) {
        final byte[] randomData = new byte[256];
        final long[] histogram = new long[256];
        int index;
        for (long n=samples/randomData.length; n-->0;) {
            randomSource.nextBytes(randomData);
            for (byte randomByte : randomData) {
                index = (int) randomByte & 0xFF;
                histogram[index]++;
            }
        }
        return histogram;
    }

    /**
     * Compute histogram of first byte from a random source to test if the values at an index is
     * likely to be uniformly distributed. This assumes the random source reseeds on each call,
     * thus it is not strictly applicable to SecureRandomSingleton which reseeds according to its
     * own schedule.
     * @param randomSource
     * @param samples Number of samples
     * @return Histogram of random values for assessing distribution
     */
    private final static long[] histogramOfValue(RandomSource randomSource, long samples) {
        final byte[] randomData = new byte[1];
        final long[] histogram = new long[256];
        int index;
        for (long n=samples/randomData.length; n-->0;) {
            randomSource.nextBytes(randomData);
            index = (int) randomData[0] & 0xFF;
            histogram[index]++;
        }
        return histogram;
    }

    /**
     * Measure speed performance of random source in generating random long values.
     * @param randomSource
     * @param samples
     * @return Average time per call in nanoseconds
     */
    private final static long nextLongPerformance(RandomSource randomSource, long samples) {
        final long timeStart = System.nanoTime();
        for (long n=samples; n-->0;) {
            randomSource.nextLong();
        }
        final long timeEnd = System.nanoTime();
        final long timePerCall = (timeEnd - timeStart) / samples;
        System.out.println("nextLongPerformance(randomSource="+randomSource.getClass().getSimpleName()+",samples="+samples+",speed="+timePerCall+"ns/call");
        return timePerCall;
    }

    /**
     * Test if histogram is uniformly distributed, where every bin has the same frequency count.
     * @param histogram
     * @return Mean absolute error as percentage of mean value, where 0 means perfectly uniform, and 1 means non-uniform.
     */
    private final static double isUniformDistribution(final long[] histogram) {
        // Calculate mean
        long samples = 0;
        for (long frequency : histogram) {
            samples += frequency;
        }
        final double mean = samples / (double) histogram.length;
        // Calculate mean absolute error
        double error = 0;
        for (long frequency : histogram) {
            error += Math.abs(frequency - mean);
        }
        error /= (double) histogram.length;
        // Report error as percentage of mean
        return (samples > 0 ? error / mean : 0);
    }
}
