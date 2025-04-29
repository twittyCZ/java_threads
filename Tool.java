package PGS.SEM_1;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Třída obsahující pomocné funkcionality
 */
public class Tool {

    private static final Map<String, Object> fileLocks = new ConcurrentHashMap<>();
    public static final String CHAPTER = "CHAPTER";
    public static final String VOLUME = "VOLUME";
    public static final String BOOK = "BOOK";
    private static String BOOK_NAME;

    // Mapování čísel zapsané slovem na integery
    private static final Map<String, Integer> ordinalMap = new HashMap<>();
    static {
        ordinalMap.put("FIRST", 1);
        ordinalMap.put("SECOND", 2);
        ordinalMap.put("THIRD", 3);
        ordinalMap.put("FOURTH", 4);
        ordinalMap.put("FIFTH", 5);
        ordinalMap.put("SIXTH", 6);
        ordinalMap.put("SEVENTH", 7);
        ordinalMap.put("EIGHTH", 8);
        ordinalMap.put("NINTH", 9);
        ordinalMap.put("TENTH", 10);
        ordinalMap.put("ELEVENTH", 11);
        ordinalMap.put("TWELFTH", 12);
        ordinalMap.put("THIRTEENTH", 13);
        ordinalMap.put("FOURTEENTH", 14);
        ordinalMap.put("FIFTEENTH", 15);
        ordinalMap.put("SIXTEENTH", 16);
        ordinalMap.put("SEVENTEENTH", 17);
        ordinalMap.put("EIGHTEENTH", 18);
        ordinalMap.put("NINTEENTH", 19);
        ordinalMap.put("TWENTY", 20);
        /// případně lze doplnit další čísla
        /// Toto je v zásadě špatný způsob, ale nenapadl mě lepší
    }

    public static String getBookName() {
        return BOOK_NAME;
    }

    public static void setBookName(String bookName) {
        Pattern pattern = Pattern.compile("^([^-]+)(?:-.*)?\\.txt$");
        Matcher matcher = pattern.matcher(bookName);
        if (matcher.find()) {
            String book = matcher.group(1);
            System.out.println("Název knihy: " + book);
            BOOK_NAME = book;
        } else {
            System.out.println("Název knihy nebyl nalezen.");
        }
    }

    /**
     * Vrací zámek pro daný soubor, pokud neexistuje, vytvoří jej.
     */
    public static Object getLockForFile(String filePath) {
        return fileLocks.computeIfAbsent(filePath, key -> new Object());
    }

    /**
     * Ošetření kritické sekce
     */
    public static void appendStateMessage(String filePath, String message) {
        Object fileLock = getLockForFile(filePath);
        synchronized (fileLock) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath, true))) {
                writer.append(message);
            } catch (IOException e) {
                System.err.println("Chyba při zápisu do " + filePath);
                e.printStackTrace();
            }
        }
    }

    /**
     * Bezpečné vytvoření adresáře
     * Pokud adresář existuje a odpovídá očekávanému názvu (tj. začíná názvem knihy),
     * smaže jej před vytvořením nového
     */
    public static void createDirectory(String directoryName) {
        File directory = new File(directoryName);
        if (directory.exists()) {
            // Kontrola, zda adresář patří aplikaci (začíná názvem knihy), abychom nesmazali nechtěné soubory
            if (directory.getName().startsWith(BOOK_NAME)) {
                deleteDirectory(directory);
            } else {
                System.out.println("Adresář " + directoryName + " neodpovídá očekávanému názvu, nebyl smazán.");
                return;
            }
        }
        if (directory.mkdir()) {
            System.out.println("Adresář úspěšně vytvořen: " + directoryName);
        } else {
            System.out.println("Adresář se nepodařilo vytvořit: " + directoryName);
        }
    }

    /**
     * Rekurzivně smaže adresář a jeho obsah
     */
    private static void deleteDirectory(File file) {
        if (file.isDirectory()) {
            File[] contents = file.listFiles();
            if (contents != null) {
                for (File f : contents) {
                    deleteDirectory(f);
                }
            }
        }
        if (!file.delete()) {
            System.out.println("Nepodařilo se smazat: " + file.getAbsolutePath());
        }
    }

    /**
     * Dle hledané značky type najde v předaném textu číslo kapitoly/knihy atp.
     */
    public static int findNumber(String type, String text) {
        Pattern pattern;
        if(type.equals(VOLUME)) {
            pattern = Pattern.compile("(?m)^VOLUME\\s+([IVXLCDM]+)[—-]");
        } else if(type.equals(BOOK)) {
            pattern = Pattern.compile("(?m)^BOOK\\s+([A-Z]+)[—-]");
        } else if(type.equals(CHAPTER)) {
            pattern = Pattern.compile("(?m)^CHAPTER\\s+([IVXLCDM]+)[—-]");
        } else throw new RuntimeException("Neznámý typ: " + type);
        Matcher matcher = pattern.matcher(text);

        int number = 0;
        if (matcher.find()) {
            if(type.equals(CHAPTER) || type.equals(VOLUME)) {
                String romanNumeral = matcher.group(1);
                number = romanToInt(romanNumeral);
            } else {
                number = ordinalToNumber(matcher.group(1));
            }
        }
        return number;
    }

    /**
     * Metoda pro převod římského čísla na int
     */
    public static int romanToInt(String roman) {
        int result = 0;
        int prev = 0;
        for (int i = roman.length() - 1; i >= 0; i--) {
            int value = romanCharToInt(roman.charAt(i));
            if (value < prev) {
                result -= value;
            } else {
                result += value;
            }
            prev = value;
        }
        return result;
    }

    /**
     * Pomocná metoda pro převod jednotlivých římských znaků
     */
    private static int romanCharToInt(char c) {
        switch(c) {
            case 'I': return 1;
            case 'V': return 5;
            case 'X': return 10;
            case 'L': return 50;
            case 'C': return 100;
            case 'D': return 500;
            case 'M': return 1000;
            default: return 0;
        }
    }

    /**
     * Převádí slovem vyjádřené číslo na integer
     */
    public static int ordinalToNumber(String ordinalWord) {
        ordinalWord = ordinalWord.toUpperCase();  // zajištění konzistence
        Integer value = ordinalMap.get(ordinalWord);
        if (value != null) {
            return value;
        } else {
            throw new RuntimeException("Neznámé klíčové slovo: " + ordinalWord);
        }
    }

    /**
     * Vytvoří příslušný state.txt soubor
     */
    public static void createStateFile(String filePath) {
        File file = new File(filePath);
        try {
            if (!file.exists()) {
                file.createNewFile();
            } else {
                file.delete();
                file.createNewFile();
            }
        } catch (IOException e) {
            throw new RuntimeException("Chyba při vytváření state.txt", e);
        }
    }
}
