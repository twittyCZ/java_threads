package PGS.SEM_1;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

public class Master implements Callable<Map<String, Integer>> {

    private String pathDirectory;
    private String book;
    private int number;
    private Underboss parent;
    private ExecutorService foremanPool;
    private BlockingQueue<String> chapterQueue = new LinkedBlockingQueue<>();

    public Master(String book, int number, Underboss parent) {
        this.book = book;
        this.number = number;
        this.parent = parent;
        this.foremanPool = Executors.newFixedThreadPool(Main.FOREMAN_NUMBER);
        chapterQueue.addAll(Arrays.asList(splitBookIntoChapters()));
        this.pathDirectory = Tool.getBookName() + "/volume" + parent.getNumber() + "/book" + number;
    }

    public int getNumber() {
        return number;
    }

    public Underboss getParent() {
        return parent;
    }

    @Override
    public Map<String, Integer> call() throws Exception {
        Map<String, Integer> results = new HashMap<>();
        List<Future<Map<String, Integer>>> futures = new ArrayList<>();
        Tool.createDirectory(pathDirectory);
        Tool.createStateFile(Tool.getBookName() + "/volume" + parent.getNumber() + "/book" + number + "/" + "state.txt");

        while (!chapterQueue.isEmpty()) { ///  předání knih ke zpracování
            String chapter = chapterQueue.poll();
            if (chapter != null) {
                Future<Map<String, Integer>> future = foremanPool.submit(new Foreman(chapter, Tool.findNumber(Tool.CHAPTER, chapter), this));
                futures.add(future);
            }
        }
        for (Future<Map<String, Integer>> future : futures) {
            try {
                ///  sloučení výsledků
                Map<String, Integer> foremanResult = future.get();
                for (Map.Entry<String, Integer> entry : foremanResult.entrySet()) {
                    results.merge(entry.getKey(), entry.getValue(), Integer::sum);
                }
            } catch (InterruptedException | ExecutionException e) {
                System.err.println("Chyba při zpracování Foreman úlohy:");
                e.printStackTrace();
            }
        }
        foremanPool.shutdown();
        try {
            if (!foremanPool.awaitTermination(60, TimeUnit.SECONDS)) {
                foremanPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            foremanPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        writeIntoFile(results);
        Tool.appendStateMessage(Tool.getBookName() + "/" + "state.txt", "Volume " + parent.getNumber() + " - Book " + number + " - OK\n");
        Tool.appendStateMessage(Tool.getBookName() + "/volume" + parent.getNumber() + "/" + "state.txt", "Book " + number + " - OK\n");
        return results;
    }

    /**
     * Rozdělí knihu na kapitoly
     */
    private String[] splitBookIntoChapters() {
        String[] chapters = book.split("(?m)(?=^CHAPTER\\s+[IVXLCDM]+[—-])");
        return Arrays.stream(chapters)
                .map(String::trim)
                .filter(chapter -> !chapter.isEmpty())
                .toArray(String[]::new);
    }

    /**
     * Zapsání výsledků do soboru
     */
    private void writeIntoFile(Map<String, Integer> wordCount) {
        ///  Treemap kvůli abecednímu řazení
        Map<String, Integer> sortedMap = new TreeMap<>(wordCount);
        String fileName = "book" + number + ".txt";
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
