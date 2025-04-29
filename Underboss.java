package PGS.SEM_1;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

public class Underboss implements Callable<Map<String, Integer>> {

    private String pathDirectory;
    private String volume;
    private int number;
    private Boss parent;
    private ExecutorService masterPool;
    private BlockingQueue<String> bookQueue = new LinkedBlockingQueue<>();

    public Underboss(String volume, int number, Boss parent) {
        this.volume = volume;
        this.number = number;
        this.parent = parent;
        this.masterPool = Executors.newFixedThreadPool(Main.MASTER_NUMBER);
        bookQueue.addAll(Arrays.asList(splitVolumeIntoBooks()));
        this.pathDirectory = Tool.getBookName() + "/volume" + number;
    }

    public int getNumber() {
        return number;
    }

    public Boss getParent() {
        return parent;
    }

    @Override
    public Map<String, Integer> call() throws Exception {
        Map<String, Integer> results = new HashMap<>();
        List<Future<Map<String, Integer>>> futures = new ArrayList<>();
        Tool.createDirectory(pathDirectory);
        Tool.createStateFile(Tool.getBookName() + "/" + "volume" + number + "/" + "state.txt");

        while (!bookQueue.isEmpty()) {
            String book = bookQueue.poll();
            if (book != null) {
                ///  předání knihy k dalšímu zpracování
                Future<Map<String, Integer>> future = masterPool.submit(new Master(book, Tool.findNumber(Tool.BOOK, book), this));
                futures.add(future);
            }
        }

        ///  sběr výsledků od jednotlivých vláken
        for (Future<Map<String, Integer>> future : futures) {
            try {
                Map<String, Integer> masterResult = future.get();
                /// sloučení výsledků do hlavní mapy
                for (Map.Entry<String, Integer> entry : masterResult.entrySet()) {
                    results.merge(entry.getKey(), entry.getValue(), Integer::sum);
                }
            } catch (InterruptedException | ExecutionException e) {
                System.err.println("Chyba při zpracování Master:");
                e.printStackTrace();
            }
        }
        masterPool.shutdown();
        try {
            ///  kontrola zprávného ukončení
            if (!masterPool.awaitTermination(60, TimeUnit.SECONDS)) {
                masterPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            masterPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        writeIntoFile(results);
        Tool.appendStateMessage(Tool.getBookName() + "/" + "state.txt", "Volume " + number + " - OK\n");
        return results;
    }

    /**
     * Rozdělí svazek na kapitoly
     */
    private String[] splitVolumeIntoBooks() {
        String[] books = volume.split("(?m)(?=^BOOK\\s+[A-Z]+[—-])");
        return Arrays.stream(books)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }

    /**
     * Zapíše slova a jejich četnost do mapy
     */
    private void writeIntoFile(Map<String, Integer> wordCount) {
        Map<String, Integer> sortedMap = new TreeMap<>(wordCount);
        String fileName = "volume" + number + ".txt";
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(pathDirectory + "/" + fileName, false))) {
            for (Map.Entry<String, Integer> entry : sortedMap.entrySet()) {
                writer.write(entry.getKey() + " " + entry.getValue());
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("Chyba při zápisu do " + fileName);
            e.printStackTrace();
        }
    }
}
