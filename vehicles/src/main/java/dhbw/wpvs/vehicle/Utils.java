package dhbw.wpvs.vehicle;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Konstanten und Hilfsmethoden.
 */
public class Utils {

    // Hilfsklasse zum Protokollieren von Fehlern
    public static final Logger LOGGER = Logger.getLogger(Utils.class.getName());

    // Adresse des MQTT-Brokers und Namen der Topics
    public static final String MQTT_BROKER_ADDRESS = "tcp://localhost:1883"; // tcp://iot.eclipse.org:1883";
    public static final String MQTT_TOPIC_NAME = "VehicleTracking";

    // Stream für Tastatureingaben
    public static BufferedReader fromKeyboard = new BufferedReader(new InputStreamReader(System.in));
    
    /**
     * Exception ausgeben
     *
     * @param t Exception
     */
    public static void logException(Throwable t) {
        LOGGER.log(Level.SEVERE, null, t);
    }

    /**
     * Einlesen von Tastatureingaben.
     * 
     * @param prompt Frage
     * @param defaultValue Vorschlagswert
     * @return Eingegebener Wert oder Vorschlagswert
     */
    public static String askInput(String prompt, String defaultValue) {
        String value;

        if (defaultValue != null & !defaultValue.trim().isEmpty()) {
            prompt += " [" + defaultValue + "]";
        }

        System.out.print(prompt + ": ");

        try {
            value = fromKeyboard.readLine();
        } catch (IOException ex) {
            Utils.logException(ex);
            value = null;
        }

        if (value == null || value.trim().isEmpty()) {
            value = defaultValue;
        }

        return value;
    }

}
