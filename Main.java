package PGS.SEM_1;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;

public class Main {

    /// Počty vláken pro jednotlivé úrovně
    /// Nejedná se o celkový počet vláken
    /// např. slave_number = n, znamená že každý foreman bude mít n slaveů
    public static final int BOSS_NUMBER = 2;
    public static final int UNDERBOSS_NUMBER = 2;
    public static final int MASTER_NUMBER = 2;
    public static final int FOREMAN_NUMBER = 2;
    public static final int SLAVE_NUMBER = 2;

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Zadejte název souboru s knihou jako parametr při spuštění.");
            return;
        }
        String filePath = args[0];
        String bookText = "";
        try { ///  načtení knihy do Stringu
            bookText = Files.readString(Paths.get(filePath));
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        Tool.setBookName(filePath);
        Tool.createDirectory(Tool.getBookName());
        Tool.createStateFile(Tool.getBookName() + "/" + "state.txt");

        // Inicializace sdílené fronty se svazky a bariéry před spuštěním Boss instancí
        Boss.initialize(bookText);
        /// vytvoření a spuštění Bossů
        ExecutorService bossPool = Executors.newFixedThreadPool(BOSS_NUMBER);
        List<Future<Map<String, Integer>>> bossFutures = new ArrayList<>();
        for (int i = 0; i < BOSS_NUMBER; i++) {
            Boss boss = new Boss(i + 1);
            Future<Map<String, Integer>> future = bossPool.submit(boss);
            bossFutures.add(future);
        }

        for (Future<Map<String, Integer>> future : bossFutures) {
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                System.err.println("Chyba při zpracování Boss úlohy:");
                e.printStackTrace();
            }
        }
        bossPool.shutdown();
        try {
            if (!bossPool.awaitTermination(60, TimeUnit.SECONDS)) {
                bossPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            bossPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
