package io.github.gohoski.notpipe.api;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import io.github.gohoski.notpipe.NotPipe;
import io.github.gohoski.notpipe.R;
import io.github.gohoski.notpipe.config.Config;
import io.github.gohoski.notpipe.config.ConfigManager;
import io.github.gohoski.notpipe.data.Video;
import io.github.gohoski.notpipe.http.HttpClient;
import io.github.gohoski.notpipe.http.VideoTooLongException;
import io.github.gohoski.notpipe.Utils;

/**
 * Manager class for handling multiple API instances with fallback capability.
 * <p>
 * Instances answer at wildly different speeds: a server on the other side of the world needs
 * seconds while a freshly banned one never answers at all. To keep the user from waiting for a
 * full timeout of a dead server, requests are now hedged: the fastest known instance is asked
 * first and, if it stays silent, backup instances are asked in parallel and the first answer
 * wins.
 */
public class Manager {
    /** How many instances may be asked at the same time. */
    private static final int MAX_PARALLEL_ATTEMPTS = 3;
    /** How many different instances may be asked for one request in total. */
    private static final int MAX_ATTEMPTS = 4;
    /** Timeout for loading video metadata, must be a bit longer than HttpClient.TIMEOUT. */
    private static final int METADATA_TIMEOUT = 25000;

    private static Manager instance;
    private Random random;
    private ConfigManager configManager;

    private List<Metadata> metadataInstances;
    private List<VideoStream> videoInstances; //360p only
    private List<VideoStream> hqInstances; //high-quality instances (480p+)
    private List<Conversion> conversionInstances; // Tracks instances capable of conversion
    private List<ChannelApi> channelApiInstances; // Tracks instances capable of ChannelApi
    private List<AudioStream> audioInstances; // Tracks instances capable of audio-only streams
    private long lastDeadInstanceToastAt = 0;

    private Manager() {
        random = new Random();
    }

    public static void init() {
        if (instance == null) {
            instance = new Manager();
            instance.initializeInstances();
        }
    }

    public static Manager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Manager.init() must be called first!");
        }
        return instance;
    }

    private void initializeInstances() {
        if (metadataInstances == null) {
            metadataInstances = new ArrayList<Metadata>();
            videoInstances = new ArrayList<VideoStream>();
            hqInstances = new ArrayList<VideoStream>();
            conversionInstances = new ArrayList<Conversion>();
            channelApiInstances = new ArrayList<ChannelApi>();
            audioInstances = new ArrayList<AudioStream>();
        } else {
            metadataInstances.clear();
            videoInstances.clear();
            hqInstances.clear();
            conversionInstances.clear();
            channelApiInstances.clear();
            audioInstances.clear();
        }

        // Latency/failure scores belong to the previous list of hosts.
        InstanceStats.clear();

        if (configManager == null) {
            configManager = ConfigManager.getInstance();
        }

        Config config = configManager.getConfig();

        List<String> invInstances = config.getInvidiousInstances();
        for (int i = 0; i < invInstances.size(); i++) {
            Invidious inv = new Invidious(invInstances.get(i));
            metadataInstances.add(inv);
            videoInstances.add(inv);
            audioInstances.add(inv);
        }

        List<String> yt2009List = config.getYt2009Instances();
        for (int i = 0; i < yt2009List.size(); i++) {
            Yt2009 yt2009 = new Yt2009(yt2009List.get(i));
            videoInstances.add(yt2009);
            hqInstances.add(yt2009);
            conversionInstances.add(yt2009);
            channelApiInstances.add(yt2009);
        }

        List<String> pipedList = config.getPipedInstances();
        for (int i = 0; i < pipedList.size(); i++) {
            String[] parts = pipedList.get(i).split(",", 2);
            String baseUrl = parts[0];
            String proxyUrl = parts.length > 1 ? parts[1] : parts[0];
            Piped piped = new Piped(baseUrl, proxyUrl);
            metadataInstances.add(piped);
            videoInstances.add(piped);
            audioInstances.add(piped);
        }

        List<String> ytApiInstances = config.getYtApiLegacyInstances();
        for (int i = 0; i < ytApiInstances.size(); i++) {
            YtApiLegacy ytApi = new YtApiLegacy(ytApiInstances.get(i));
            metadataInstances.add(ytApi);
            videoInstances.add(ytApi);
            hqInstances.add(ytApi);
            conversionInstances.add(ytApi);
        }
    }

    public Metadata getMetadata() {
        if (metadataInstances.isEmpty()) throw new IllegalStateException("No Metadata instances");
        return createStatefulFallbackProxy(Metadata.class, metadataInstances);
    }

    /**
     * Retrieves a single raw Metadata instance without a dynamic fallback proxy wrapper.
     * Useful for optional single-attempt fallback operations (like channel icons).
     */
    public Metadata getRandomMetadata() {
        if (metadataInstances == null || metadataInstances.isEmpty()) {
            return null;
        }
        return metadataInstances.get(random.nextInt(metadataInstances.size()));
    }

    /**
     * Retrieves a Metadata proxy prioritizing YtApiLegacy instances.
     * Returns null if no YtApiLegacy instances are configured.
     */
    public Metadata getPopularMetadata() {
        if (metadataInstances == null || metadataInstances.isEmpty()) {
            return null;
        }

        List<Metadata> ytApiList = new ArrayList<Metadata>();
        for (int i = 0; i < metadataInstances.size(); i++) {
            Metadata m = metadataInstances.get(i);
            if (m instanceof YtApiLegacy) {
                ytApiList.add(m);
            }
        }

        if (ytApiList.isEmpty()) {
            return null;
        }

        return createStatefulFallbackProxy(Metadata.class, ytApiList);
    }

    public VideoStream getVideoStream() {
        if (videoInstances.isEmpty()) throw new IllegalStateException("No VideoStream instances");
        return createStatefulFallbackProxy(VideoStream.class, videoInstances);
    }

    public ChannelApi getChannelApi() {
        if (channelApiInstances.isEmpty()) throw new IllegalStateException("No ChannelApi instances");
        return createStatefulFallbackProxy(ChannelApi.class, channelApiInstances);
    }

    /**
     * Tells whether at least one metadata-capable instance is configured.
     * Activities must check this before calling {@link #getMetadata()}, otherwise a broken
     * instance list (for example a list containing only yt2009) crashes the app on start.
     *
     * @return true if video metadata can be requested
     */
    public boolean hasMetadataInstances() {
        return metadataInstances != null && !metadataInstances.isEmpty();
    }

    /**
     * Tells whether at least one instance can serve audio-only streams.
     *
     * @return true if audio downloads are possible
     */
    public boolean hasAudioInstances() {
        return audioInstances != null && !audioInstances.isEmpty();
    }

    /**
     * Resolves an audio-only stream URL, asking the fastest audio-capable instances first.
     *
     * @param id The YouTube video ID
     * @param timeout Timeout in milliseconds for a single attempt
     * @return Direct audio URL
     * @throws IOException If no instance could provide an audio stream
     */
    public String getAudioUrl(final String id, int timeout) throws IOException {
        if (audioInstances == null || audioInstances.isEmpty()) {
            throw new IOException("No audio instances configured");
        }

        final List<AudioStream> candidates = rankedCopy(audioInstances, null);
        RaceOutcome<String> outcome = race(candidates, new Attempt<String>() {
            @Override
            public String run(Object instance) throws Exception {
                return ((AudioStream) instance).getAudioUrl(id);
            }
        }, timeout, true);

        if (outcome.value != null) return outcome.value;
        throw failureToException(outcome, "Failed to resolve audio stream");
    }

    public List<Conversion> getConversion() {
        return conversionInstances;
    }

    /**
     * Loads video metadata, asking several instances in parallel when the first one is slow.
     *
     * @param id The YouTube video ID
     * @return Parsed video
     * @throws IOException If every instance failed or the video is unavailable
     */
    public Video getVideo(final String id) throws IOException {
        if (metadataInstances == null || metadataInstances.isEmpty()) {
            throw new IOException("No metadata instances left");
        }

        final List<Metadata> candidates = rankedCopy(metadataInstances, null);

        RaceOutcome<Video> outcome = race(candidates, new Attempt<Video>() {
            @Override
            public Video run(Object instance) throws Exception {
                return ((Metadata) instance).getVideo(id);
            }
        }, METADATA_TIMEOUT, true);

        if (outcome.value != null) return outcome.value;
        throw failureToException(outcome, "Failed to load video info");
    }

    public String getVideoUrl(String videoId, String quality, int timeout) throws IOException {
        return getVideoUrl(videoId, quality, timeout, null, null);
    }

    public String getVideoUrl(String videoId, String quality, int timeout, VideoStream preferredInstance, VideoStream[] successfulInstance) throws IOException {
        return resolveStream(videoId, quality, timeout, preferredInstance, successfulInstance, true);
    }

    /**
     * Resolves a stream URL quietly, without removing instances or showing toasts.
     * Used to warm the cache while the user is still reading the video page.
     *
     * @param videoId The YouTube video ID
     * @param quality Requested quality
     * @param timeout Timeout in milliseconds
     * @param preferredInstance Instance to ask first, may be null
     * @param successfulInstance Out parameter receiving the instance that answered
     * @return The stream URL
     * @throws IOException If no instance could resolve the video
     */
    public String prefetchVideoUrl(String videoId, String quality, int timeout, VideoStream preferredInstance, VideoStream[] successfulInstance) throws IOException {
        return resolveStream(videoId, quality, timeout, preferredInstance, successfulInstance, false);
    }

    private String resolveStream(final String videoId, String quality, int timeout, VideoStream preferredInstance, VideoStream[] successfulInstance, boolean announce) throws IOException {
        final List<VideoStream> pool = "360".equals(quality) ? videoInstances : hqInstances;
        if (pool.isEmpty()) {
            reloadInstances();
            throw new IOException("No video instances left");
        }

        final List<VideoStream> candidates = rankedCopy(pool, preferredInstance);
        final String requestedQuality = quality;
        final int attemptTimeout = timeout;

        RaceOutcome<String> outcome = race(candidates, new Attempt<String>() {
            @Override
            public String run(Object instance) throws Exception {
                return ((VideoStream) instance).getVideoUrl(videoId, requestedQuality, attemptTimeout);
            }
        }, timeout, announce);

        if (outcome.value != null) {
            if (successfulInstance != null && successfulInstance.length > 0 && outcome.instance instanceof VideoStream) {
                successfulInstance[0] = (VideoStream) outcome.instance;
            }
            return outcome.value;
        }

        if (announce && (videoInstances.isEmpty() || hqInstances.isEmpty())) {
            reloadInstances();
            showConnectionErrorToast();
        }

        throw failureToException(outcome, "All instances failed");
    }

    public String getConvUrl(String videoId, String quality, int codec) throws IOException {
        return getConvUrl(videoId, quality, codec, null, null);
    }

    public String getConvUrl(String videoId, String quality, int codec, VideoStream preferredInstance, VideoStream[] successfulInstance) throws IOException {
        if (configManager.getConfig().isConvertVideos()) {
            if (conversionInstances.isEmpty()) {
                reloadInstances();
                throw new IOException("No conversion instances left");
            }

            List<Conversion> targetConvList = new ArrayList<Conversion>(conversionInstances);

            // Prioritize the preferred instance if it supports conversion
            if (preferredInstance instanceof Conversion && targetConvList.contains((Conversion) preferredInstance)) {
                targetConvList.remove((Conversion) preferredInstance);
                targetConvList.add(0, (Conversion) preferredInstance);
                if (targetConvList.size() > 1) {
                    List<Conversion> rest = targetConvList.subList(1, targetConvList.size());
                    java.util.Collections.shuffle(rest, random);
                }
            } else {
                java.util.Collections.shuffle(targetConvList, random);
            }

            for (int i = 0; i < targetConvList.size(); i++) {
                Conversion currentInstance = targetConvList.get(i);
                try {
                    String url = currentInstance.getConvUrl(videoId, codec);
                    if (successfulInstance != null && successfulInstance.length > 0 && currentInstance instanceof VideoStream) {
                        successfulInstance[0] = (VideoStream) currentInstance;
                    }
                    return url;
                } catch (Exception e) {
                    if (e instanceof ContentUnavailableException) {
                        throw (ContentUnavailableException) e;
                    } if (e instanceof VideoTooLongException) {
                        throw (VideoTooLongException) e;
                    } conversionInstances.remove(currentInstance);
                    if (isDeadInstanceError(e)) {
                        removeDeadInstance(currentInstance);
                    }
                }
            }
        }

        // Fall back to standard stream URLs if conversion is disabled or if all conversion attempts fail
        return getVideoUrl(videoId, quality, HttpClient.CONVERSION_TIMEOUT, preferredInstance, successfulInstance);
    }

    /**
     * Returns a shuffled copy of the given pool ordered by the measured response time of every
     * instance, optionally moving the preferred instance to the front.
     */
    private <T> List<T> rankedCopy(List<T> pool, T preferred) {
        List<T> ranked = new ArrayList<T>(pool);
        Collections.shuffle(ranked, random);
        Collections.sort(ranked, new Comparator<T>() {
            @Override
            public int compare(T left, T right) {
                return InstanceStats.scoreOf(hostOf(left)) - InstanceStats.scoreOf(hostOf(right));
            }
        });
        if (preferred != null) {
            int index = -1;
            for (int i = 0; i < ranked.size(); i++) {
                if (ranked.get(i) == preferred) {
                    index = i;
                    break;
                }
            }
            if (index > 0) {
                ranked.remove(index);
                ranked.add(0, preferred);
            }
        }
        return ranked;
    }

    /**
     * Hedged request: asks the first candidate right away and, if it does not answer within
     * the network-specific delay, asks backups in parallel. The first successful answer wins,
     * so a single slow or dead instance no longer blocks playback for a whole minute.
     */
    private <T> RaceOutcome<T> race(List<?> candidates, Attempt<T> attempt, int timeout, boolean announce) throws IOException {
        RaceState<T> state = new RaceState<T>(candidates, attempt, Math.min(candidates.size(), MAX_ATTEMPTS), announce);
        int hedgeDelay = Utils.getHedgeDelay(NotPipe.getAppContext());
        long deadline = System.currentTimeMillis() + Math.max(timeout, hedgeDelay);
        boolean cancelled = false;

        synchronized (state.lock) {
            while (true) {
                if (!state.finished[0] && state.active[0] == 0 && state.next[0] < state.limit) {
                    startRacer(state, state.next[0]++);
                }
                if (state.finished[0]) break;

                long now = System.currentTimeMillis();
                if (now >= deadline) break;
                if (state.active[0] == 0 && state.next[0] >= state.limit) {
                    state.finished[0] = true;
                    break;
                }

                long waitTime = deadline - now;
                if (waitTime <= 0) {
                    // Object.wait(0) blocks forever, so the deadline must be handled explicitly.
                    state.finished[0] = true;
                    break;
                }
                if (waitTime > hedgeDelay) waitTime = hedgeDelay;
                try {
                    state.lock.wait(Math.max(1L, waitTime));
                } catch (InterruptedException e) {
                    cancelled = true;
                    state.finished[0] = true;
                    Thread.currentThread().interrupt();
                    break;
                }

                if (!state.finished[0] && state.active[0] < MAX_PARALLEL_ATTEMPTS && state.next[0] < state.limit) {
                    startRacer(state, state.next[0]++);
                }
            }
        }

        if (cancelled) {
            throw new IOException("Request cancelled while resolving stream");
        }
        return state.outcome;
    }

    private <T> void startRacer(RaceState<T> state, int index) {
        state.active[0]++;
        Thread thread = new Thread(new Racer<T>(state, index), "notPipe-resolve");
        thread.setDaemon(true);
        thread.start();
    }

    private class Racer<T> implements Runnable {
        private final RaceState<T> state;
        private final int index;

        Racer(RaceState<T> state, int index) {
            this.state = state;
            this.index = index;
        }

        @Override
        public void run() {
            Object instance = null;
            boolean considerDead = false;
            long started = System.currentTimeMillis();
            try {
                instance = state.candidates.get(index);
                T value = state.attempt.run(instance);
                long elapsed = System.currentTimeMillis() - started;
                synchronized (state.lock) {
                    if (!state.finished[0]) {
                        state.outcome.value = value;
                        state.outcome.instance = instance;
                        state.finished[0] = true;
                        InstanceStats.recordSuccess(hostOf(instance), elapsed);
                    }
                    state.active[0]--;
                    state.lock.notifyAll();
                }
            } catch (Exception e) {
                synchronized (state.lock) {
                    boolean alreadyDecided = state.finished[0];
                    state.active[0]--;
                    state.outcome.lastError = e;
                    state.outcome.failures++;
                    if (e instanceof FileNotFoundException) state.outcome.notFound++;
                    if (isFatal(e)) {
                        state.outcome.fatal = e;
                        state.finished[0] = true;
                    } else {
                        InstanceStats.recordFailure(hostOf(instance));
                        considerDead = !alreadyDecided;
                    }
                    if (!state.finished[0] && state.active[0] == 0 && state.next[0] >= state.limit) {
                        state.finished[0] = true;
                    }
                    state.lock.notifyAll();
                }
                if (considerDead && isDeadInstanceError(e) && state.announce) {
                    removeDeadInstance(instance);
                }
            }
        }
    }

    /** Exceptions that mean the content itself is gone, so no other instance is asked. */
    private boolean isFatal(Throwable t) {
        return t instanceof ContentUnavailableException || t instanceof VideoTooLongException;
    }

    private IOException failureToException(RaceOutcome<?> outcome, String fallbackMessage) {
        if (outcome.fatal instanceof IOException) return (IOException) outcome.fatal;
        Throwable lastError = outcome.lastError;
        if (outcome.failures > 0 && outcome.notFound == outcome.failures) {
            return new FileNotFoundException(lastError != null ? lastError.getMessage() : fallbackMessage);
        }
        if (lastError instanceof IOException) return (IOException) lastError;
        if (lastError != null && lastError.getMessage() != null) return new IOException(lastError.getMessage());
        return new IOException(fallbackMessage);
    }

    private String hostOf(Object instance) {
        if (instance == null) return "";
        try {
            return (String) instance.getClass().getMethod("getHost").invoke(instance);
        } catch (Exception ignored) {
            return "";
        }
    }

    private interface Attempt<T> {
        T run(Object instance) throws Exception;
    }

    private static class RaceOutcome<T> {
        T value;
        Object instance;
        Throwable fatal;
        Throwable lastError;
        int failures;
        int notFound;
    }

    private static class RaceState<T> {
        final Object lock = new Object();
        final List<?> candidates;
        final Attempt<T> attempt;
        final RaceOutcome<T> outcome = new RaceOutcome<T>();
        final boolean[] finished = new boolean[]{false};
        final int[] active = new int[]{0};
        final int[] next = new int[]{0};
        final int limit;
        final boolean announce;

        RaceState(List<?> candidates, Attempt<T> attempt, int limit, boolean announce) {
            this.candidates = candidates;
            this.attempt = attempt;
            this.limit = limit;
            this.announce = announce;
        }
    }

    /**
     * Creates a dynamic proxy that retains one instance for the lifespan of the Activity.
     */
    @SuppressWarnings("unchecked")
    private <T> T createStatefulFallbackProxy(final Class<T> interfaceClass, final List<T> pool) {
        return (T) Proxy.newProxyInstance(
                interfaceClass.getClassLoader(),
                new Class<?>[]{ interfaceClass },
                new InvocationHandler() {
                    private T currentInstance = null;
                    private void pickNewInstance() {
                        if (!pool.isEmpty()) {
                            currentInstance = pool.get(random.nextInt(pool.size()));
                        } else {
                            currentInstance = null;
                        }
                    }

                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if (currentInstance == null) pickNewInstance();

                        Throwable lastError = null;
                        List<T> localPool = new ArrayList<T>(pool);

                        while (!localPool.isEmpty()) {
                            if (currentInstance == null || !localPool.contains(currentInstance)) {
                                currentInstance = localPool.get(random.nextInt(localPool.size()));
                            }

                            try {
                                long started = System.currentTimeMillis();
                                Object result = method.invoke(currentInstance, args);
                                InstanceStats.recordSuccess(hostOf(currentInstance), System.currentTimeMillis() - started);
                                return result;
                            } catch (InvocationTargetException e) {
                                if (e.getCause() instanceof ContentUnavailableException) {
                                    throw e.getCause();
                                }
                                e.printStackTrace();
                                lastError = e.getCause();
                                localPool.remove(currentInstance);
                                InstanceStats.recordFailure(hostOf(currentInstance));

                                if (isDeadInstanceError(lastError)) {
                                    removeDeadInstance(currentInstance);
                                }
                                currentInstance = null;
                            }
                        }

                        if (pool.isEmpty()) {
                            reloadInstances();
                            showConnectionErrorToast();
                        }

                        if (lastError != null) throw lastError;
                        throw new IOException("No available instances left for " + interfaceClass.getSimpleName());
                    }
                }
        );
    }

    public void removeDeadInstance(Object instance) {
        final String host;
        try {
            host = (String) instance.getClass().getMethod("getHost").invoke(instance);
            notifyDeadInstance(host);
        } catch (Exception ignored) { return; }
        removeByHost(metadataInstances, host);
        removeByHost(videoInstances, host);
        removeByHost(hqInstances, host);
        removeByHost(conversionInstances, host);
        removeByHost(channelApiInstances, host);
        removeByHost(audioInstances, host);
    }

    private void removeByHost(List list, String host) {
        java.util.Iterator iterator = list.iterator();
        while (iterator.hasNext()) {
            try {
                Object item = iterator.next();
                String itemHost = (String) item.getClass().getMethod("getHost").invoke(item);
                if (host.equals(itemHost)) {
                    iterator.remove();
                }
            } catch (Exception ignored) {}
        }
    }

    private void notifyDeadInstance(final String name) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    Context context = NotPipe.getAppContext();
                    if (context != null) {
                        // Several instances can die at once (for example after the phone wakes up
                        // from sleep); showing a toast for each of them would only cover the screen.
                        long now = System.currentTimeMillis();
                        if (now - lastDeadInstanceToastAt < 5000) return;
                        lastDeadInstanceToastAt = now;
                        Toast.makeText(context,
                                context.getString(R.string.dead_instance, name),
                                Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void showConnectionErrorToast() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    Toast.makeText(NotPipe.getAppContext(),
                            "check your internet connection",
                            Toast.LENGTH_SHORT).show();
                } catch (Exception ignored) {}
            }
        });
    }

    private boolean isDeadInstanceError(Throwable t) {
        if (t == null) return false;

        if (!io.github.gohoski.notpipe.Utils.hasConnection(NotPipe.getAppContext())) {
            return false;
        }

        if (t instanceof SocketTimeoutException ||
                t instanceof ConnectException ||
                t instanceof UnknownHostException) {
            return true;
        }

        return false;
    }

    public void reloadInstances() {
        initializeInstances();
    }

    public List<VideoStream> getVideoInstances() {
        return videoInstances;
    }

    public List<VideoStream> getHqInstances() {
        return hqInstances;
    }

    public static class InstanceInfo {
        public VideoStream instance;
        public String host;
        public String name;
        public String baseUrl;
        public boolean supportsAllQualities;
        public boolean supportsMetadata;
        public boolean supportsVideo;
        public boolean supportsHq;
        public boolean supportsConversion;
        public boolean supportsChannels;
        public boolean supportsAudio;
    }

    public List<InstanceInfo> videoInstancesInfo() {
        initializeInstances();
        List<InstanceInfo> result = new ArrayList<InstanceInfo>();
        List<VideoStream> allKnown = new ArrayList<VideoStream>();
        List<String> processed = new ArrayList<String>();
        allKnown.addAll(videoInstances);
        allKnown.addAll(hqInstances);

        for (int i = 0; i < allKnown.size(); i++) {
            VideoStream instance = allKnown.get(i);
            InstanceInfo info = new InstanceInfo();
            info.instance = instance;
            info.host = instance.getHost();
            info.name = instance.getName();
            info.supportsAllQualities = !(instance instanceof Invidious) && !(instance instanceof Piped);
            if (!processed.contains(info.host)) {
                result.add(info);
                processed.add(info.host);
            }
        }

        return result;
    }

    private static final int FLAG_METADATA = 1;
    private static final int FLAG_VIDEO = 2;
    private static final int FLAG_HQ = 4;
    private static final int FLAG_CONVERSION = 8;
    private static final int FLAG_CHANNEL = 16;
    private static final int FLAG_AUDIO = 32;

    /**
     * Returns every configured instance once, with all capabilities it provides.
     *
     * @return List of instances for the diagnostics screen
     */
    public List<InstanceInfo> allInstancesInfo() {
        initializeInstances();
        List<InstanceInfo> result = new ArrayList<InstanceInfo>();
        List<String> hosts = new ArrayList<String>();

        collect(result, hosts, metadataInstances, FLAG_METADATA);
        collect(result, hosts, videoInstances, FLAG_VIDEO);
        collect(result, hosts, hqInstances, FLAG_HQ);
        collect(result, hosts, conversionInstances, FLAG_CONVERSION);
        collect(result, hosts, channelApiInstances, FLAG_CHANNEL);
        collect(result, hosts, audioInstances, FLAG_AUDIO);
        return result;
    }

    private void collect(List<InstanceInfo> result, List<String> hosts, List<?> list, int flags) {
        if (list == null) return;
        List<?> snapshot = new ArrayList<Object>(list);

        for (int i = 0; i < snapshot.size(); i++) {
            Object instance = snapshot.get(i);
            String host = hostOf(instance);
            if (host.length() == 0) continue;

            int index = hosts.indexOf(host);
            InstanceInfo info;
            if (index >= 0) {
                info = result.get(index);
            } else {
                info = new InstanceInfo();
                info.host = host;
                info.name = nameOf(instance);
                if (instance instanceof BaseUrl) {
                    info.baseUrl = ((BaseUrl) instance).getBaseUrl();
                }
                if (instance instanceof VideoStream) {
                    info.instance = (VideoStream) instance;
                }
                hosts.add(host);
                result.add(info);
            }
            applyFlags(info, flags);
        }
    }

    private void applyFlags(InstanceInfo info, int flags) {
        if ((flags & FLAG_METADATA) != 0) info.supportsMetadata = true;
        if ((flags & FLAG_VIDEO) != 0) info.supportsVideo = true;
        if ((flags & FLAG_HQ) != 0) {
            info.supportsHq = true;
            info.supportsAllQualities = true;
        }
        if ((flags & FLAG_CONVERSION) != 0) info.supportsConversion = true;
        if ((flags & FLAG_CHANNEL) != 0) info.supportsChannels = true;
        if ((flags & FLAG_AUDIO) != 0) info.supportsAudio = true;
    }

    private String nameOf(Object instance) {
        try {
            return (String) instance.getClass().getMethod("getName").invoke(instance);
        } catch (Exception e) {
            return hostOf(instance);
        }
    }

    /**
     * Returns the measured latency of an instance for the diagnostics screen.
     *
     * @param host Instance host
     * @return Latency in milliseconds or -1
     */
    public int latencyOf(String host) {
        return InstanceStats.latencyOf(host);
    }

    /**
     * Returns the number of consecutive failures of an instance.
     *
     * @param host Instance host
     * @return Failure count
     */
    public int failuresOf(String host) {
        return InstanceStats.failuresOf(host);
    }

    /**
     * Forgets all measured latencies and failures.
     */
    public void resetStats() {
        InstanceStats.clear();
    }
}
