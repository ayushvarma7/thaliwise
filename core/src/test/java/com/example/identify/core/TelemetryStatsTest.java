package com.example.identify.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class TelemetryStatsTest {

    private static TelemetryStats.Sample s(long wall, long cpu, long rss, long ua) {
        return new TelemetryStats.Sample(wall, cpu, rss, ua);
    }

    @Test
    public void coresAndPeaks() {
        List<TelemetryStats.Sample> samples = Arrays.asList(
                s(0, 0, 100, -1_000_000),
                s(1000, 2000, 300, -2_000_000),
                s(2000, 6000, 200, -3_000_000));
        TelemetryStats.Summary sum = TelemetryStats.summarize(samples, false);
        assertEquals(3, sum.samples);
        assertEquals(2000, sum.wallMs);
        assertEquals(6000, sum.cpuMs);
        assertEquals(3.0, sum.avgCores, 1e-9);
        assertEquals(4.0, sum.peakCores, 1e-9);
        assertEquals(100, sum.startRssKb);
        assertEquals(200, sum.endRssKb);
        assertEquals(300, sum.peakRssKb);
        assertEquals(-2000.0, sum.avgCurrentMa, 1e-9);
        assertEquals(3000.0, sum.peakCurrentMa, 1e-9);
        // trapezoid: (1.5 A + 2.5 A) * 1 s = 4 A*s = 4000 mA*s = 1.111 mAh
        assertEquals(4000.0 / 3600.0, sum.energyMah, 1e-9);
    }

    @Test
    public void positiveDischargeConventionGivesSameEnergy() {
        List<TelemetryStats.Sample> samples = Arrays.asList(s(0, 0, 1, 1_000_000), s(3_600_000, 0, 1, 1_000_000));
        assertEquals(1000.0, TelemetryStats.summarize(samples, false).energyMah, 1e-9);
    }

    @Test
    public void chargingOrMissingCurrentGivesNoEnergy() {
        List<TelemetryStats.Sample> samples = Arrays.asList(s(0, 0, 1, 500_000), s(1000, 10, 1, 500_000));
        assertTrue(Double.isNaN(TelemetryStats.summarize(samples, true).energyMah));
        List<TelemetryStats.Sample> missing = Arrays.asList(
                s(0, 0, 1, TelemetryStats.NO_CURRENT), s(1000, 10, 1, TelemetryStats.NO_CURRENT));
        TelemetryStats.Summary sum = TelemetryStats.summarize(missing, false);
        assertTrue(Double.isNaN(sum.avgCurrentMa));
        assertTrue(Double.isNaN(sum.energyMah));
    }

    @Test
    public void emptySamples() {
        TelemetryStats.Summary sum = TelemetryStats.summarize(Arrays.asList(), false);
        assertEquals(0, sum.samples);
        assertEquals(0.0, sum.avgCores, 0.0);
    }

    @Test
    public void clustersAreNamedSlowestFirst() {
        long[] freqs = {1704000, 1704000, 1704000, 1704000, 2367000, 2367000, 2367000, 2367000, 2915000};
        List<TelemetryStats.Cluster> c = TelemetryStats.clusters(freqs);
        assertEquals(3, c.size());
        assertEquals("little", c.get(0).name);
        assertEquals(Arrays.asList(0, 1, 2, 3), c.get(0).cpus);
        assertEquals("mid", c.get(1).name);
        assertEquals("big", c.get(2).name);
        assertEquals(Arrays.asList(8), c.get(2).cpus);
        assertEquals(2, TelemetryStats.clusterOf(c, 8));
        assertEquals(-1, TelemetryStats.clusterOf(c, 42));
    }

    @Test
    public void unknownFrequenciesGetOwnGroup() {
        List<TelemetryStats.Cluster> c = TelemetryStats.clusters(new long[]{1000, -1, 1000});
        assertEquals(2, c.size());
        assertEquals("all", c.get(0).name);
        assertEquals("unknown", c.get(1).name);
        assertEquals(Arrays.asList(1), c.get(1).cpus);
    }

    @Test
    public void fastCoresSkipTheSlowestCluster() {
        // Pixel 8 maximum clocks: 4 little, 4 mid, 1 big
        long[] pixel8 = {1704000, 1704000, 1704000, 1704000, 2367000, 2367000, 2367000, 2367000, 2914000};
        assertEquals(5, TelemetryStats.fastCoreCount(TelemetryStats.clusters(pixel8)));
        assertEquals(4, TelemetryStats.fastCoreCount(TelemetryStats.clusters(
                new long[]{1000, 1000, 1000, 1000, 2000, 2000, 2000, 2000})));
        assertEquals(8, TelemetryStats.fastCoreCount(TelemetryStats.clusters(
                new long[]{2000, 2000, 2000, 2000, 2000, 2000, 2000, 2000})));
        assertEquals(2, TelemetryStats.fastCoreCount(TelemetryStats.clusters(new long[]{0, 0})));
        assertEquals(1, TelemetryStats.fastCoreCount(TelemetryStats.clusters(new long[0])));
    }
}
