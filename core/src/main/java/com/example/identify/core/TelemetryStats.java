package com.example.identify.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Pure math over telemetry samples: CPU cores in use, memory peaks, battery current and energy. */
public final class TelemetryStats {
    private TelemetryStats() {}

    /** Marks a battery current reading that the device did not provide. */
    public static final long NO_CURRENT = Long.MIN_VALUE;

    public static final class Sample {
        public final long wallMs;
        public final long cpuMs;        // process CPU time, all threads
        public final long rssKb;
        public final long currentUa;    // battery current in microamps, NO_CURRENT if unavailable

        public Sample(long wallMs, long cpuMs, long rssKb, long currentUa) {
            this.wallMs = wallMs;
            this.cpuMs = cpuMs;
            this.rssKb = rssKb;
            this.currentUa = currentUa;
        }
    }

    public static final class Summary {
        public final int samples;
        public final long wallMs;
        public final long cpuMs;
        /** CPU time divided by wall time: how many cores were busy on average. */
        public final double avgCores;
        /** Highest busy-core count between two consecutive samples. */
        public final double peakCores;
        public final long startRssKb;
        public final long endRssKb;
        public final long peakRssKb;
        /** Mean battery current in mA (sign as reported by the device), NaN if unavailable. */
        public final double avgCurrentMa;
        /** Largest current magnitude in mA, NaN if unavailable. */
        public final double peakCurrentMa;
        /** Charge drawn from the battery in mAh, NaN if unavailable or the phone was charging. */
        public final double energyMah;

        Summary(int samples, long wallMs, long cpuMs, double avgCores, double peakCores,
                long startRssKb, long endRssKb, long peakRssKb,
                double avgCurrentMa, double peakCurrentMa, double energyMah) {
            this.samples = samples;
            this.wallMs = wallMs;
            this.cpuMs = cpuMs;
            this.avgCores = avgCores;
            this.peakCores = peakCores;
            this.startRssKb = startRssKb;
            this.endRssKb = endRssKb;
            this.peakRssKb = peakRssKb;
            this.avgCurrentMa = avgCurrentMa;
            this.peakCurrentMa = peakCurrentMa;
            this.energyMah = energyMah;
        }
    }

    /** Cores busy over an interval. 0 when the interval is empty. */
    public static double cores(long cpuMs, long wallMs) {
        return wallMs <= 0 ? 0.0 : (double) cpuMs / wallMs;
    }

    /**
     * Summarizes samples taken in time order. Energy uses the trapezoid rule over the current readings and
     * is only reported when the phone was discharging, because a charger hides what the app itself draws.
     * Android reports discharge current as negative on most devices, so the drawn charge is the
     * negated integral.
     */
    public static Summary summarize(List<Sample> samples, boolean charging) {
        int n = samples.size();
        if (n == 0) {
            return new Summary(0, 0, 0, 0.0, 0.0, 0, 0, 0, Double.NaN, Double.NaN, Double.NaN);
        }
        Sample first = samples.get(0);
        Sample last = samples.get(n - 1);
        long wall = last.wallMs - first.wallMs;
        long cpu = last.cpuMs - first.cpuMs;

        double peakCores = 0.0;
        long peakRss = 0;
        for (int i = 0; i < n; i++) {
            Sample s = samples.get(i);
            peakRss = Math.max(peakRss, s.rssKb);
            if (i > 0) {
                Sample p = samples.get(i - 1);
                peakCores = Math.max(peakCores, cores(s.cpuMs - p.cpuMs, s.wallMs - p.wallMs));
            }
        }

        double currentSum = 0.0;
        int currentCount = 0;
        double peakCurrent = Double.NaN;
        double chargeMaH = 0.0;
        boolean integrable = true;
        for (int i = 0; i < n; i++) {
            Sample s = samples.get(i);
            if (s.currentUa == NO_CURRENT) {
                integrable = false;
                continue;
            }
            double ma = s.currentUa / 1000.0;
            currentSum += ma;
            currentCount++;
            peakCurrent = Double.isNaN(peakCurrent) ? Math.abs(ma) : Math.max(peakCurrent, Math.abs(ma));
            if (i > 0) {
                Sample p = samples.get(i - 1);
                if (p.currentUa == NO_CURRENT) continue;
                double hours = (s.wallMs - p.wallMs) / 3_600_000.0;
                chargeMaH += (p.currentUa + s.currentUa) / 2000.0 * hours;
            }
        }
        double avgCurrent = currentCount == 0 ? Double.NaN : currentSum / currentCount;
        double energy = (!charging && integrable && currentCount >= 2) ? Math.abs(Math.min(chargeMaH, 0.0)) : Double.NaN;

        return new Summary(n, wall, cpu, cores(cpu, wall), peakCores,
                first.rssKb, last.rssKb, peakRss, avgCurrent, peakCurrent, energy);
    }

    /** A group of CPU cores that share a maximum frequency (for example little, mid, big). */
    public static final class Cluster {
        public final String name;
        public final long maxKhz;
        public final List<Integer> cpus;

        public Cluster(String name, long maxKhz, List<Integer> cpus) {
            this.name = name;
            this.maxKhz = maxKhz;
            this.cpus = cpus;
        }
    }

    /**
     * Groups CPU indexes by maximum frequency, slowest first. Unknown frequencies (zero or negative) go to
     * a final "unknown" group. Three groups are named little, mid, big; two are little, big; one is all.
     */
    public static List<Cluster> clusters(long[] maxKhzPerCpu) {
        TreeMap<Long, List<Integer>> byFreq = new TreeMap<>();
        List<Integer> unknown = new ArrayList<>();
        for (int cpu = 0; cpu < maxKhzPerCpu.length; cpu++) {
            long f = maxKhzPerCpu[cpu];
            if (f <= 0) {
                unknown.add(cpu);
            } else {
                byFreq.computeIfAbsent(f, k -> new ArrayList<>()).add(cpu);
            }
        }
        String[] names;
        int k = byFreq.size();
        if (k == 1) names = new String[]{"all"};
        else if (k == 2) names = new String[]{"little", "big"};
        else if (k == 3) names = new String[]{"little", "mid", "big"};
        else {
            names = new String[k];
            for (int i = 0; i < k; i++) names[i] = "cluster" + i;
        }
        List<Cluster> out = new ArrayList<>();
        int i = 0;
        for (Map.Entry<Long, List<Integer>> e : byFreq.entrySet()) {
            out.add(new Cluster(names[i++], e.getKey(), e.getValue()));
        }
        if (!unknown.isEmpty()) out.add(new Cluster("unknown", 0, unknown));
        return out;
    }

    /** Index of the cluster that contains the CPU, or -1. */
    public static int clusterOf(List<Cluster> clusters, int cpu) {
        for (int i = 0; i < clusters.size(); i++) {
            if (clusters.get(i).cpus.contains(cpu)) return i;
        }
        return -1;
    }
}
