package server;

import org.junit.jupiter.api.Test;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DataManagerTest {

    @Test
    void testPutAndGet() {
        DataManager dataManager = new DataManager();

        long startTime = System.nanoTime();
        assertTrue(dataManager.put("key1", "value1".getBytes()));
        long endTime = System.nanoTime();
        System.out.printf("Tempo de execução do put: %.2f ms%n", (endTime - startTime) / 1e6);

        startTime = System.nanoTime();
        assertEquals("value1", new String(dataManager.get("key1")));
        endTime = System.nanoTime();
        System.out.printf("Tempo de execução do get: %.2f ms%n", (endTime - startTime) / 1e6);

        startTime = System.nanoTime();
        assertNull(dataManager.get("key2"));
        endTime = System.nanoTime();
        System.out.printf("Tempo de execução do get (chave inexistente): %.2f ms%n", (endTime - startTime) / 1e6);
    }

    @Test
    void testMultiPutAndMultiGet() {
        DataManager dataManager = new DataManager();

        // MultiPut - Insere múltiplos pares chave-valor
        Map<String, byte[]> valuesToInsert = new HashMap<>();
        valuesToInsert.put("key1", "value1".getBytes());
        valuesToInsert.put("key2", "value2".getBytes());
        valuesToInsert.put("key3", "value3".getBytes());

        long startTime = System.nanoTime();
        dataManager.multiPut(valuesToInsert);
        long endTime = System.nanoTime();
        System.out.printf("Tempo de execução do multiPut: %.2f ms%n", (endTime - startTime) / 1e6);

        // Verifica se as chaves foram inseridas corretamente
        assertEquals("value1", new String(dataManager.get("key1")));
        assertEquals("value2", new String(dataManager.get("key2")));
        assertEquals("value3", new String(dataManager.get("key3")));

        // MultiGet - Recupera múltiplas chaves
        Set<String> keysToRetrieve = new HashSet<>(Arrays.asList("key1", "key2", "key4")); // key4 não existe
        long multiGetStartTime = System.nanoTime();
        Map<String, byte[]> retrievedValues = dataManager.multiGet(keysToRetrieve);
        long multiGetEndTime = System.nanoTime();
        System.out.printf("Tempo de execução do multiGet: %.2f ms%n", (multiGetEndTime - multiGetStartTime) / 1e6);

        // Verifica os valores retornados
        assertEquals("value1", new String(retrievedValues.get("key1")));
        assertEquals("value2", new String(retrievedValues.get("key2")));
        assertNull(retrievedValues.get("key4"));
    }

    @Test
    void testGetWhen() throws InterruptedException {
        DataManager dataManager = new DataManager();

        // Insere as chaves e valores necessários
        dataManager.put("condKey", "condValue".getBytes());
        dataManager.put("targetKey", "targetValue".getBytes());

        // Cria uma thread para atualizar a condição depois de um atraso
        Thread updater = new Thread(() -> {
            try {
                Thread.sleep(500); // Espera antes de atualizar a condição
                dataManager.put("condKey", "updatedValue".getBytes());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        updater.start();

        // Testa a função getWhen (espera a condição ser satisfeita)
        long startTime = System.nanoTime();
        byte[] value = dataManager.getWhen("targetKey", "condKey", "updatedValue".getBytes());
        long endTime = System.nanoTime();

        assertEquals("targetValue", new String(value));
        System.out.printf("Tempo de execução do getWhen: %.2f ms%n", (endTime - startTime) / 1e6);

        updater.join(); // Aguarda a conclusão da thread
    }
}
