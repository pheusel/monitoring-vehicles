package dhbw.wpvs.vehicle;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Main {

    public static void main(String[] args) throws Exception {

        // Fahrzeug-ID abfragen
        String vehicleId = Utils.askInput("Beliebige Fahrzeug-ID", "postauto");

        // Zu fahrende Strecke abfragen
        File workdir = new File("./waypoints");
        String[] waypointFiles = workdir.list((File dir, String name) -> {
            return name.toLowerCase().endsWith(".itn");
        });

        System.out.println();
        System.out.println("Aktuelles Verzeichnis: " + workdir.getCanonicalPath());
        System.out.println();
        System.out.println("Verfügbare Wegstrecken");
        System.out.println();

        for (int i = 0; i < waypointFiles.length; i++) {
            System.out.println("  [" + i + "] " + waypointFiles[i]);
        }

        System.out.println();
        int index = Integer.parseInt(Utils.askInput("Zu fahrende Strecke", "0"));
        
        List<WGS84> waypoints = parseItnFile(new File(workdir, waypointFiles[index]));

        // Adresse des MQTT-Brokers abfragen
        String mqttAddress = Utils.askInput("MQTT-Broker", Utils.MQTT_BROKER_ADDRESS);

        // TODO: Sicherstellen, dass bei einem Verbindungsabbruch eine sog.
        // LastWill-Nachricht gesendet wird, die auf den Verbindungsabbruch
        // hinweist. Die Nachricht soll eine "StatusMessage" sein, bei der das
        // Feld "type" auf "StatusType.CONNECTION_LOST" gesetzt ist.
        //
        // Die Nachricht muss dem MqttConnectOptions-Objekt übergeben werden
        // und soll an das Topic Utils.MQTT_TOPIC_NAME gesendet werden.
        
        // TODO: Verbindung zum MQTT-Broker herstellen.

        MqttClient client = new MqttClient(mqttAddress, MqttClient.generateClientId());

        client.connect();

        // TODO: Statusmeldung mit "type" = "StatusType.VEHICLE_READY" senden.

        StatusMessage statusMessage = new StatusMessage();
        statusMessage.type = StatusType.VEHICLE_READY;

        MqttMessage message = new MqttMessage(statusMessage.toJson());
        client.publish(Utils.MQTT_TOPIC_NAME, message);


        // Die Nachricht soll soll an das Topic Utils.MQTT_TOPIC_NAME gesendet
        // werden.
        
        // TODO: Thread starten, der jede Sekunde die aktuellen Sensorwerte
        // des Fahrzeugs ermittelt und verschickt. Die Sensordaten sollen
        // an das Topic Utils.MQTT_TOPIC_NAME + "/" + vehicleId gesendet werden.
        Vehicle vehicle = new Vehicle(vehicleId, waypoints);
        vehicle.startVehicle();

        // Warten, bis das Programm beendet werden soll
        Utils.fromKeyboard.readLine();

        vehicle.stopVehicle();
        
        // TODO: Oben vorbereitete LastWill-Nachricht hier manuell versenden,
        // da sie bei einem regulären Verbindungsende nicht automatisch
        // verschickt wird.
        //
        // Anschließend die Verbindung trennen und den oben gestarteten Thread
        // beenden, falls es kein Daemon-Thread ist.
    }

    /**
     * Öffnet die in "filename" übergebene ITN-Datei und extrahiert daraus die
     * Koordinaten für die Wegstrecke des Fahrzeugs. Das Dateiformat ist ganz
     * simpel:
     *
     * <pre>
     * 0845453|4902352|Point 1 |0|
     * 0848501|4900249|Point 2 |0|
     * 0849295|4899460|Point 3 |0|
     * 0849796|4897723|Point 4 |0|
     * </pre>
     *
     * Jede Zeile enthält einen Wegpunkt. Die Datenfelder einer Zeile werden
     * durch | getrennt. Das erste Feld ist die "Longitude", das zweite Feld die
     * "Latitude". Die Zahlen müssen durch 100_000.0 geteilt werden.
     *
     * @param file ITN-Datei
     * @return Liste mit Koordinaten
     * @throws java.io.IOException
     */
    public static List<WGS84> parseItnFile(File file) throws IOException {
        List<WGS84> waypoints = new ArrayList<>();

        BufferedReader br  = new BufferedReader(new FileReader(file));
        String line;

        do{
            line = br.readLine();
            if(!line.startsWith("<")){
                String[] as = line.split("|");
                WGS84 wgs84 = new WGS84();
                wgs84.longitude = Double.valueOf(as[0]) / 100000.0;
                wgs84.latitude = Double.valueOf(as[1]) / 100000.0;
                waypoints.add(wgs84);
            }
        } while (line != null);

        return waypoints;
    }
}
