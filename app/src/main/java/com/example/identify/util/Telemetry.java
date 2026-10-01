package com.example.identify.util;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Debug;
import android.os.PowerManager;
import android.os.SystemClock;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.example.identify.Config;
import com.example.identify.core.TelemetryStats;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Device and process measurements: CPU time and placement, memory, battery, temperature, thermal state. */
public final class Telemetry {
    private Telemetry() {}

    private static final long CLOCK_TICKS_PER_S = clockTicks();
    private static volatile List<TelemetryStats.Cluster> clusters;

    // ---------------------------------------------------------------- point readings

    /** CPU time used by every thread of this process, in ms. */
    public static long processCpuMs() {
        return android.os.Process.getElapsedCpuTime();
    }

    public static final class ProcStatus {
        public long rssKb;
        public long hwmKb;
        public long rssAnonKb;
        public long rssFileKb;
        public long swapKb;
        public int threads;
    }

    /** Parses /proc/self/status. Fields that cannot be read stay 0. */
    public static ProcStatus procStatus() {
        ProcStatus s = new ProcStatus();
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/self/status"))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("VmRSS:")) s.rssKb = firstNumber(line);
                else if (line.startsWith("VmHWM:")) s.hwmKb = firstNumber(line);
                else if (line.startsWith("RssAnon:")) s.rssAnonKb = firstNumber(line);
                else if (line.startsWith("RssFile:")) s.rssFileKb = firstNumber(line);
                else if (line.startsWith("VmSwap:")) s.swapKb = firstNumber(line);
                else if (line.startsWith("Threads:")) s.threads = (int) firstNumber(line);
            }
        } catch (IOException e) {
            Log.w(Config.LOG_TAG, "cannot read /proc/self/status", e);
        }
        return s;
    }

    public static int cpuCount() {
        String possible = readLine("/sys/devices/system/cpu/possible");   // for example "0-8"
        if (possible != null) {
            int dash = possible.lastIndexOf('-');
            try {
                return Integer.parseInt(possible.substring(dash + 1).trim()) + 1;
            } catch (NumberFormatException ignored) {
                // fall through to the runtime count
            }
        }
        return Runtime.getRuntime().availableProcessors();
    }

    /** CPU cores grouped by maximum frequency (little, mid, big on a Pixel 8). Read once. */
    public static List<TelemetryStats.Cluster> clusters() {
        List<TelemetryStats.Cluster> c = clusters;
        if (c == null) {
            int n = cpuCount();
            long[] max = new long[n];
            for (int i = 0; i < n; i++) max[i] = readLong("/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_max_freq");
            c = TelemetryStats.clusters(max);
            clusters = c;
        }
        return c;
    }

    public static final class Battery {
        public long currentUa = TelemetryStats.NO_CURRENT;
        public int levelPct = -1;
        public boolean charging;
        public float tempC = Float.NaN;
    }

    public static Battery battery(Context ctx, boolean withTemperature) {
        Battery b = new Battery();
        BatteryManager bm = (BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);
        if (bm != null) {
            int cur = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            if (cur != Integer.MIN_VALUE) b.currentUa = cur;
            b.levelPct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            b.charging = bm.isCharging();
        }
        if (withTemperature) {
            Intent sticky = ContextCompat.registerReceiver(ctx, null,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED);
            if (sticky != null) {
                int t = sticky.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
                if (t != Integer.MIN_VALUE) b.tempC = t / 10f;
            }
        }
        return b;
    }

    public static int thermalStatus(Context ctx) {
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        return pm == null ? -1 : pm.getCurrentThermalStatus();
    }

    /** Forecast headroom (1.0 means throttling starts). NaN if unsupported. Rate-limited by Android: about 1 Hz. */
    public static float thermalHeadroom(Context ctx) {
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        return pm == null ? Float.NaN : pm.getThermalHeadroom(0);
    }

    public static String thermalName(int status) {
        switch (status) {
            case PowerManager.THERMAL_STATUS_NONE: return "none";
            case PowerManager.THERMAL_STATUS_LIGHT: return "light";
            case PowerManager.THERMAL_STATUS_MODERATE: return "moderate";
            case PowerManager.THERMAL_STATUS_SEVERE: return "severe";
            case PowerManager.THERMAL_STATUS_CRITICAL: return "critical";
            case PowerManager.THERMAL_STATUS_EMERGENCY: return "emergency";
            case PowerManager.THERMAL_STATUS_SHUTDOWN: return "shutdown";
            default: return "unknown";
        }
    }

    public static ActivityManager.MemoryInfo deviceMemory(Context ctx) {
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        if (am != null) am.getMemoryInfo(mi);
        return mi;
    }

    /** The device as seen by the experiment log. */
    public static JSONObject deviceJson(Context ctx) {
        JSONObject d = new JSONObject();
        ExperimentLog.put(d, "manufacturer", android.os.Build.MANUFACTURER);
        ExperimentLog.put(d, "model", android.os.Build.MODEL);
        ExperimentLog.put(d, "device", android.os.Build.DEVICE);
        ExperimentLog.put(d, "soc", android.os.Build.SOC_MODEL);
        ExperimentLog.put(d, "sdk", android.os.Build.VERSION.SDK_INT);
        ExperimentLog.put(d, "release", android.os.Build.VERSION.RELEASE);
        ExperimentLog.put(d, "n_cpus", cpuCount());
        JSONArray cl = new JSONArray();
        for (TelemetryStats.Cluster c : clusters()) {
            JSONObject o = new JSONObject();
            ExperimentLog.put(o, "name", c.name);
            ExperimentLog.put(o, "cpus", c.cpus.toString());
            ExperimentLog.put(o, "max_mhz", c.maxKhz / 1000);
            cl.put(o);
        }
        ExperimentLog.put(d, "clusters", cl);
        ExperimentLog.put(d, "total_ram_mb", deviceMemory(ctx).totalMem / (1024 * 1024));
        return d;
    }

    // ---------------------------------------------------------------- phase marks

    /** Wall clock and process CPU time at one instant. */
    public static final class Mark {
        public final long wallMs;
        public final long cpuMs;

        private Mark(long wallMs, long cpuMs) {
            this.wallMs = wallMs;
            this.cpuMs = cpuMs;
        }

        public static Mark now() {
            return new Mark(SystemClock.elapsedRealtime(), processCpuMs());
        }
    }

    public static JSONObject phase(String name, Mark from, Mark to) {
        JSONObject o = new JSONObject();
        long wall = to.wallMs - from.wallMs;
        long cpu = to.cpuMs - from.cpuMs;
        ExperimentLog.put(o, "name", name);
        ExperimentLog.put(o, "wall_ms", wall);
        ExperimentLog.put(o, "cpu_ms", cpu);
        ExperimentLog.put(o, "avg_cores", round2(TelemetryStats.cores(cpu, wall)));
        return o;
    }

    // ---------------------------------------------------------------- sampler

    /** What a finished sampling window measured: the full JSON and the numbers the UI shows. */
    public static final class Report {
        public final JSONObject json;
        public final TelemetryStats.Summary summary;
        public final int nCpus;
        /** For example "mid 78%, big 15%, little 7%", empty if thread placement could not be read. */
        public final String clusterShares;
        public final boolean charging;
        public final float tempEndC;
        public final String thermalMax;

        Report(JSONObject json, TelemetryStats.Summary summary, int nCpus, String clusterShares,
               boolean charging, float tempEndC, String thermalMax) {
            this.json = json;
            this.summary = summary;
            this.nCpus = nCpus;
            this.clusterShares = clusterShares;
            this.charging = charging;
            this.tempEndC = tempEndC;
            this.thermalMax = thermalMax;
        }
    }

    /** Samples the process every interval on its own thread until stop(). */
    public static final class Sampler {
        private final Context app;
        private final ScheduledExecutorService exec;
        private final List<TelemetryStats.Sample> samples = new ArrayList<>();
        private final List<TelemetryStats.Cluster> cl = clusters();
        private final long[] clusterTicks = new long[cl.size()];
        private final double[] clusterFreqSumKhz = new double[cl.size()];
        private final int[] clusterFreqCount = new int[cl.size()];
        private final Battery batteryStart;
        private final ActivityManager.MemoryInfo memStart;
        private final int thermalStart;
        private Map<Integer, long[]> lastThreads = new HashMap<>();
        private boolean threadsReadable = true;
        private long peakAnonKb;
        private long peakFileKb;
        private int thermalMax;
        private float headroomMin = Float.NaN;
        private int sampleIndex;
        private Report report;

        private Sampler(Context ctx, long intervalMs) {
            app = ctx.getApplicationContext();
            batteryStart = battery(app, true);
            memStart = deviceMemory(app);
            thermalStart = thermalStatus(app);
            thermalMax = thermalStart;
            lastThreads = readThreads();
            exec = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "telemetry"));
            exec.scheduleWithFixedDelay(this::sampleSafely, 0, intervalMs, TimeUnit.MILLISECONDS);
        }

        public static Sampler start(Context ctx, long intervalMs) {
            return new Sampler(ctx, intervalMs);
        }

        private void sampleSafely() {
            try {
                sample();
            } catch (RuntimeException e) {
                Log.w(Config.LOG_TAG, "telemetry sample failed", e);
            }
        }

        private synchronized void sample() {
            if (report != null) return;
            ProcStatus ps = procStatus();
            Battery b = battery(app, false);
            samples.add(new TelemetryStats.Sample(SystemClock.elapsedRealtime(), processCpuMs(), ps.rssKb, b.currentUa));
            peakAnonKb = Math.max(peakAnonKb, ps.rssAnonKb);
            peakFileKb = Math.max(peakFileKb, ps.rssFileKb);

            // Which cluster each thread ran on since the last sample.
            Map<Integer, long[]> now = readThreads();
            if (now.isEmpty()) threadsReadable = false;
            for (Map.Entry<Integer, long[]> e : now.entrySet()) {
                long[] prev = lastThreads.get(e.getKey());
                long delta = e.getValue()[0] - (prev == null ? 0 : prev[0]);
                int c = TelemetryStats.clusterOf(cl, (int) e.getValue()[1]);
                if (delta > 0 && c >= 0) clusterTicks[c] += delta;
            }
            lastThreads = now;

            for (int i = 0; i < cl.size(); i++) {
                for (int cpu : cl.get(i).cpus) {
                    long f = readLong("/sys/devices/system/cpu/cpu" + cpu + "/cpufreq/scaling_cur_freq");
                    if (f > 0) {
                        clusterFreqSumKhz[i] += f;
                        clusterFreqCount[i]++;
                    }
                }
            }

            thermalMax = Math.max(thermalMax, thermalStatus(app));
            if (sampleIndex % 4 == 0) {   // the headroom API is rate-limited
                float h = thermalHeadroom(app);
                if (!Float.isNaN(h)) headroomMin = Float.isNaN(headroomMin) ? h : Math.min(headroomMin, h);
            }
            sampleIndex++;
        }

        /** Stops sampling (idempotent) and returns the report. */
        public Report stop() {
            exec.shutdownNow();
            synchronized (this) {
                if (report != null) return report;
                sample();
                report = buildReport();
                return report;
            }
        }

        private Report buildReport() {
            Battery batteryEnd = battery(app, true);
            ActivityManager.MemoryInfo memEnd = deviceMemory(app);
            ProcStatus ps = procStatus();
            boolean charging = batteryStart.charging || batteryEnd.charging;
            TelemetryStats.Summary s = TelemetryStats.summarize(samples, charging);
            int nCpus = cpuCount();

            JSONObject j = new JSONObject();
            ExperimentLog.put(j, "interval_ms", Config.TELEMETRY_INTERVAL_MS);
            ExperimentLog.put(j, "samples", s.samples);

            JSONObject cpu = new JSONObject();
            ExperimentLog.put(cpu, "wall_ms", s.wallMs);
            ExperimentLog.put(cpu, "cpu_ms", s.cpuMs);
            ExperimentLog.put(cpu, "avg_cores", round2(s.avgCores));
            ExperimentLog.put(cpu, "peak_cores", round2(s.peakCores));
            ExperimentLog.put(cpu, "n_cpus", nCpus);
            ExperimentLog.put(cpu, "avg_pct_of_device", round2(100.0 * s.avgCores / nCpus));
            long totalTicks = 0;
            for (long t : clusterTicks) totalTicks += t;
            JSONArray byCluster = new JSONArray();
            StringBuilder shares = new StringBuilder();
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < cl.size(); i++) order.add(i);
            order.sort((a, b) -> Long.compare(clusterTicks[b], clusterTicks[a]));
            for (int i : order) {
                JSONObject o = new JSONObject();
                double pct = totalTicks == 0 ? 0.0 : 100.0 * clusterTicks[i] / totalTicks;
                ExperimentLog.put(o, "name", cl.get(i).name);
                ExperimentLog.put(o, "cpus", cl.get(i).cpus.toString());
                ExperimentLog.put(o, "max_mhz", cl.get(i).maxKhz / 1000);
                ExperimentLog.put(o, "cpu_ms", clusterTicks[i] * 1000 / CLOCK_TICKS_PER_S);
                ExperimentLog.put(o, "share_pct", round2(pct));
                ExperimentLog.put(o, "avg_cur_mhz", clusterFreqCount[i] == 0
                        ? null : Math.round(clusterFreqSumKhz[i] / clusterFreqCount[i] / 1000.0));
                byCluster.put(o);
                if (threadsReadable && totalTicks > 0) {
                    if (shares.length() > 0) shares.append(", ");
                    shares.append(String.format(Locale.US, "%s %.0f%%", cl.get(i).name, pct));
                }
            }
            ExperimentLog.put(cpu, "by_cluster", byCluster);
            ExperimentLog.put(j, "cpu", cpu);

            JSONObject mem = new JSONObject();
            ExperimentLog.put(mem, "rss_start_mb", s.startRssKb / 1024);
            ExperimentLog.put(mem, "rss_end_mb", s.endRssKb / 1024);
            ExperimentLog.put(mem, "rss_peak_mb", s.peakRssKb / 1024);
            ExperimentLog.put(mem, "rss_anon_peak_mb", peakAnonKb / 1024);
            ExperimentLog.put(mem, "rss_file_peak_mb", peakFileKb / 1024);
            ExperimentLog.put(mem, "vm_hwm_mb", ps.hwmKb / 1024);
            ExperimentLog.put(mem, "swap_mb", ps.swapKb / 1024);
            ExperimentLog.put(mem, "threads", ps.threads);
            ExperimentLog.put(mem, "native_heap_mb", Debug.getNativeHeapAllocatedSize() / (1024 * 1024));
            ExperimentLog.put(mem, "device_avail_start_mb", memStart.availMem / (1024 * 1024));
            ExperimentLog.put(mem, "device_avail_end_mb", memEnd.availMem / (1024 * 1024));
            ExperimentLog.put(mem, "device_low_memory", memStart.lowMemory || memEnd.lowMemory);
            ExperimentLog.put(j, "memory", mem);

            JSONObject bat = new JSONObject();
            ExperimentLog.put(bat, "charging", charging);
            ExperimentLog.put(bat, "level_start_pct", batteryStart.levelPct);
            ExperimentLog.put(bat, "level_end_pct", batteryEnd.levelPct);
            ExperimentLog.put(bat, "temp_start_c", (double) batteryStart.tempC);
            ExperimentLog.put(bat, "temp_end_c", (double) batteryEnd.tempC);
            ExperimentLog.put(bat, "current_avg_ma", round2(s.avgCurrentMa));
            ExperimentLog.put(bat, "current_peak_ma", round2(s.peakCurrentMa));
            ExperimentLog.put(bat, "energy_mah", round2(s.energyMah));
            if (charging) ExperimentLog.put(bat, "note", "charging: current and energy include the charger");
            ExperimentLog.put(j, "battery", bat);

            JSONObject th = new JSONObject();
            ExperimentLog.put(th, "status_start", thermalName(thermalStart));
            ExperimentLog.put(th, "status_max", thermalName(thermalMax));
            ExperimentLog.put(th, "headroom_min", (double) headroomMin);
            ExperimentLog.put(j, "thermal", th);

            return new Report(j, s, nCpus, shares.toString(), charging, batteryEnd.tempC, thermalName(thermalMax));
        }
    }

    // ---------------------------------------------------------------- live view (Settings)

    /** One reading for the Settings screen, with CPU cores busy since the previous reading. */
    public static final class Live {
        public final Mark mark;
        public final double cores;
        public final int nCpus;
        public final long rssMb;
        public final long hwmMb;
        public final float tempC;
        public final boolean charging;
        public final String thermal;

        Live(Mark mark, double cores, int nCpus, long rssMb, long hwmMb, float tempC, boolean charging, String thermal) {
            this.mark = mark;
            this.cores = cores;
            this.nCpus = nCpus;
            this.rssMb = rssMb;
            this.hwmMb = hwmMb;
            this.tempC = tempC;
            this.charging = charging;
            this.thermal = thermal;
        }
    }

    public static Live live(Context ctx, Mark previous) {
        Mark now = Mark.now();
        double cores = previous == null ? 0.0
                : TelemetryStats.cores(now.cpuMs - previous.cpuMs, now.wallMs - previous.wallMs);
        ProcStatus ps = procStatus();
        Battery b = battery(ctx, true);
        return new Live(now, cores, cpuCount(), ps.rssKb / 1024, ps.hwmKb / 1024, b.tempC, b.charging,
                thermalName(thermalStatus(ctx)));
    }

    // ---------------------------------------------------------------- helpers

    /** tid to {utime + stime in clock ticks, CPU the thread last ran on}. Empty if /proc is not readable. */
    private static Map<Integer, long[]> readThreads() {
        Map<Integer, long[]> out = new HashMap<>();
        File[] tasks = new File("/proc/self/task").listFiles();
        if (tasks == null) return out;
        for (File t : tasks) {
            String stat = readLine(new File(t, "stat").getPath());
            if (stat == null) continue;
            int close = stat.lastIndexOf(')');   // the thread name may contain spaces
            if (close < 0) continue;
            String[] f = stat.substring(close + 2).split(" ");
            // after the name: f[0] is field 3 (state); utime 14, stime 15, processor 39
            if (f.length < 37) continue;
            try {
                long ticks = Long.parseLong(f[11]) + Long.parseLong(f[12]);
                long cpu = Long.parseLong(f[36]);
                out.put(Integer.parseInt(t.getName()), new long[]{ticks, cpu});
            } catch (NumberFormatException ignored) {
                // a thread that exited mid-read
            }
        }
        return out;
    }

    private static long clockTicks() {
        try {
            long hz = Os.sysconf(OsConstants._SC_CLK_TCK);
            return hz > 0 ? hz : 100;
        } catch (RuntimeException e) {
            return 100;
        }
    }

    private static long firstNumber(String line) {
        long v = 0;
        boolean seen = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch >= '0' && ch <= '9') {
                v = v * 10 + (ch - '0');
                seen = true;
            } else if (seen) {
                break;
            }
        }
        return v;
    }

    private static String readLine(String path) {
        try (BufferedReader r = new BufferedReader(new FileReader(path))) {
            return r.readLine();
        } catch (IOException | SecurityException e) {
            return null;
        }
    }

    private static long readLong(String path) {
        String s = readLine(path);
        if (s == null) return -1;
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static Double round2(double v) {
        return Double.isNaN(v) || Double.isInfinite(v) ? Double.NaN : Math.round(v * 100.0) / 100.0;
    }
}
