package PGS.SEM_1;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

public class Slave implements Callable<Map<String, Integer>> {
    String paragraph;
    int number;

    public Slave(String paragraph) {
        this(paragraph, paragraph.length());
    }

    public Slave(String paragraph, int number) {
        this.paragraph = paragraph;
        this.number = number;
    }

    @Override
    public Map<String, Integer> call() throws Exception {
        Map<String, Integer> wordCount = new HashMap<String, Integer>();
        countWordsInParagraph(wordCount);
        return wordCount;
    }

    /**
     * Spočítá výskyt jednotlivých slov v předaném paragrafu a uloží jej do mapy
     * @param wordCount mapa slov (klíč) a čenosti
     */
    private void countWordsInParagraph(Map<String, Integer> wordCount) {
        String[] words = paragraph.toLowerCase()
                .replaceAll("-", " ")
                .replaceAll("[^a-zA-Z\\s]", "")
                .replaceAll("\\s+", " ")
                .trim()
                .split(" ");

        for (String word : words) {
            if (!word.isEmpty()) {
                wordCount.put(word, wordCount.getOrDefault(word, 0) + 1);
            }
        }
    }

}
