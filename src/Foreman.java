package PGS.SEM_1;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

public class Foreman implements Callable<Map<String, Integer>> {

    public static final String PARAGRAPHSIGN = "\\n\\n";

    private String pathDirectory;
    private String chapter;
    private int number;
    private Master parent;
    private ExecutorService slavePool;
    private BlockingQueue<String> paragraphQueue = new LinkedBlockingQueue<>();

    public Foreman(String chapter, int number, Master parent) {
        this.chapter = chapter;
        this.number = number;
        this.parent = parent;
        this.slavePool = Executors.newFixedThreadPool(Main.SLAVE_NUMBER);
        paragraphQueue.addAll(Arrays.asList(splitChapterIntoParagraphs()));
        this.pathDirectory = Tool.getBookName() + "/volume" + parent.getParent().getNumber() + "/book" + parent.getNumber();
    }

    @Override
    public Map<String, Integer> call() throws Exception {
        Map<String, Integer> results = new HashMap<>();
        List<Future<Map<String, Integer>>> futures = new ArrayList<>();
        while (!paragraphQueue.isEmpty()) {
            String paragraph = paragraphQueue.poll();
            if (paragraph != null) {
                Future<Map<String, Integer>> future = slavePool.submit(new Slave(paragraph));
                futures.add(future);
            }
        }
        for (Future<Map<String, Integer>> future : futures) {
            try {
                Map<String, Integer> slaveResult = future.get();
                for (Map.Entry<String, Integer> entry : slaveResult.entrySet()) {
                    results.merge(entry.getKey(), entry.getValue(), Integer::sum);
                }
            } catch (InterruptedException | ExecutionException e) {
                System.err.println("Chyba při zpracování Slave úlohy:");
                e.printStackTrace();
            }
        }
        slavePool.shutdown();
        try {
            if (!slavePool.awaitTermination(60, TimeUnit.SECONDS)) {
                slavePool.shutdownNow();
            }
        } catch (InterruptedException e) {
            slavePool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        ///  zápisy výsledků
        writeIntoFile(results);
        Tool.appendStateMessage(Tool.getBookName() + "/" + "state.txt", "Volume " + parent.getParent().getNumber() + " - Book " + parent.getNumber() + " - Chapter " + number + " - OK\n");
        Tool.appendStateMessage(Tool.getBookName() + "/volume" + parent.getParent().getNumber() + "/" + "state.txt", "Book " + parent.getNumber() + " - Chapter " + number + " - OK\n");
        Tool.appendStateMessage(Tool.getBookName() + "/volume" + parent.getParent().getNumber() + "/book" + parent.getNumber() + "/" + "state.txt", "Chapter " + number + " - OK\n");
        return results;
    }

    private String[] splitChapterIntoParagraphs() {
        return chapter.split(PARAGRAPHSIGN);
    }

    /**
     * Zápis výsledků do souboru
     */
    private void writeIntoFile(Map<String, Integer> wordCount) {
        Map<String, Integer> sortedMap = new TreeMap<>(wordCount);
        String fileName = "chapter" + number + ".txt";
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
