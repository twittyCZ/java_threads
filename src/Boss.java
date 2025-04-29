package PGS.SEM_1;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;

public class Boss implements Callable<Map<String, Integer>> {

    private final int number;
    private final ExecutorService underbossPool;
    private static BlockingQueue<String> sharedVolumeQueue;

    /// Sdílené výsledky mezi Bossy
    private static ConcurrentMap<String, Integer> result = new ConcurrentHashMap<>();

    /// Bariéra pro Boss úlohy
    private static CyclicBarrier barrier;

    public Boss(int number) {
        this.number = number;
        this.underbossPool = Executors.newFixedThreadPool(Main.UNDERBOSS_NUMBER);
    }

    /**
     * Inicializace sdílené fronty se svazky a bariéry před spuštěním Boss úloh.
     * @param bookText celá kniha převedena na String
     */
    public static void initialize(String bookText) {
        String[] volumes = splitFileIntoVolumes(bookText);
        sharedVolumeQueue = new LinkedBlockingQueue<>(Arrays.asList(volumes));
        barrier = new CyclicBarrier(Main.BOSS_NUMBER, () -> {
            System.out.println("Poslední Boss dokončil svou práci – zapisujeme finální výsledek.");
            writeFinalResultIntoFile(result);
        });
    }

    /**
     * Zapsání výsledných hodnot do souboru
     */
    private static void writeFinalResultIntoFile(Map<String, Integer> wordCount) {
        Map<String, Integer> sortedMap = new TreeMap<>(wordCount);
        String fileName = Tool.getBookName() + ".txt";
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(Tool.getBookName() + "/" + fileName))) {
            for (Map.Entry<String, Integer> entry : sortedMap.entrySet()) {
                writer.write(entry.getKey() + " " + entry.getValue());
                writer.newLine();
            }
            System.out.println("Výsledky byly zapsány do souboru " + fileName);
        } catch (IOException e) {
            System.err.println("Chyba při zápisu finálního výsledku:");
            e.printStackTrace();
        }
    }

    private static String[] splitFileIntoVolumes(String book) {
        // Rozdělení podle "VOLUME ..." odděleného prázdnými řádky
        String[] volumes = book.split("(?m)(?=^VOLUME[A-Z\\d—. ]+)");
        return Arrays.stream(volumes)
                .map(String::trim)
                .filter(s -> !s.isEmpty() && !s.contains("LES MISÉRABLES"))
                .toArray(String[]::new);
    }

    @Override
    public Map<String, Integer> call() throws Exception {
        Map<String, Integer> localResult = new HashMap<>();
        List<Future<Map<String, Integer>>> futures = new ArrayList<>(); // list výsledků podřízených instancí
        String volume;
        while ((volume = sharedVolumeQueue.poll()) != null) {
            int volumeNumber = Tool.findNumber(Tool.VOLUME, volume);
            Underboss underboss = new Underboss(volume, volumeNumber, this);
            Future<Map<String, Integer>> future = underbossPool.submit(underboss);
            futures.add(future);
        }
        for (Future<Map<String, Integer>> future : futures) {
            try {
                Map<String, Integer> underbossResult = future.get();
                for (Map.Entry<String, Integer> entry : underbossResult.entrySet()) {
                    localResult.merge(entry.getKey(), entry.getValue(), Integer::sum);
                }
            } catch (InterruptedException | ExecutionException e) {
                System.err.println("Chyba při zpracování dat");
                e.printStackTrace();
            }
        }
        underbossPool.shutdown();
        try {
            if (!underbossPool.awaitTermination(60, TimeUnit.SECONDS)) {
                underbossPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            underbossPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        ///  sloučení výsledků do globální mapy
        for (Map.Entry<String, Integer> entry : localResult.entrySet()) {
            result.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
        barrier.await();
        return localResult;
    }

    public int getNumber() {
        return number;
    }
}
