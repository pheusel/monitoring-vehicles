package dhbw.wpvs.vehicle;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class Main {

    public static void main(String[] args) throws Exception {

        int qos = 0;

        // Fahrzeug-ID abfragen
        String vehicleId = Utils.askInput("Beliebige Fahrzeug-ID", "postauto");

        // Zu fahrende Strecke abfragen
        File workdir = new File("./vehicles/waypoints");
        String[] waypointFiles = workdir.list((File dir, String name) -> name.toLowerCase().endsWith(".itn"));

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

        MqttClient client = new MqttClient(mqttAddress, MqttClient.generateClientId());

        // TODO: Sicherstellen, dass bei einem Verbindungsabbruch eine sog.
        // LastWill-Nachricht gesendet wird, die auf den Verbindungsabbruch
        // hinweist. Die Nachricht soll eine "StatusMessage" sein, bei der das
        // Feld "type" auf "StatusType.CONNECTION_LOST" gesetzt ist.
        //
        // Die Nachricht muss dem MqttConnectOptions-Objekt übergeben werden
        // und soll an das Topic Utils.MQTT_TOPIC_NAME gesendet werden.

        StatusMessage lwt = new StatusMessage();
        lwt.type = StatusType.CONNECTION_LOST;
        lwt.vehicleId = vehicleId;
        lwt.message = "Last Will and Testament";


        MqttConnectOptions conOp = new MqttConnectOptions();
        conOp.setCleanSession(true);
        conOp.setWill(Utils.MQTT_TOPIC_NAME, lwt.toJson(), qos, true);
        System.out.println("Connecting to broker: " + mqttAddress);

        // TODO: Verbindung zum MQTT-Broker herstellen.

        client.connect(conOp);
        System.out.println("Connected");

        // TODO: Statusmeldung mit "type" = "StatusType.VEHICLE_READY" senden.

        StatusMessage statusMessage = new StatusMessage();
        statusMessage.type = StatusType.VEHICLE_READY;
        statusMessage.vehicleId = vehicleId;
        statusMessage.message = "Register Vehicle";

        MqttMessage message = new MqttMessage(statusMessage.toJson());
        message.setQos(qos);
        client.publish(Utils.MQTT_TOPIC_NAME, message);
        System.out.println("Message was published");


        // Die Nachricht soll soll an das Topic Utils.MQTT_TOPIC_NAME gesendet
        // werden.

        // TODO: Thread starten, der jede Sekunde die aktuellen Sensorwerte
        // des Fahrzeugs ermittelt und verschickt. Die Sensordaten sollen
        // an das Topic Utils.MQTT_TOPIC_NAME + "/" + vehicleId gesendet werden.
        Vehicle vehicle = new Vehicle(vehicleId, waypoints);
        vehicle.startVehicle();

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    client.publish(Utils.MQTT_TOPIC_NAME + "/" + vehicleId, new MqttMessage(vehicle.getSensorData().toJson()));
                } catch (MqttException e) {
                    e.printStackTrace();
                }

            }
        }, 0, 1000);

        // Warten, bis das Programm beendet werden soll
        Utils.fromKeyboard.readLine();

        vehicle.stopVehicle();

        timer.cancel();

        System.out.println("Vehicle data was published");


        // TODO: Oben vorbereitete LastWill-Nachricht hier manuell versenden,
        // da sie bei einem regulären Verbindungsende nicht automatisch
        // verschickt wird.
        //
        // Anschließend die Verbindung trennen und den oben gestarteten Thread
        // beenden, falls es kein Daemon-Thread ist.

        client.publish(Utils.MQTT_TOPIC_NAME, new MqttMessage(lwt.toJson()));
        client.disconnect();
        System.out.println("Disconnected");
        System.exit(0);
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
     * <p>
     * Jede Zeile enthält einen Wegpunkt. Die Datenfelder einer Zeile werden
     * durch | getrennt. Das erste Feld ist die "Longitude", das zweite Feld die
     * "Latitude". Die Zahlen müssen durch 100_000.0 geteilt werden.
     *
     * @param file ITN-Datei
     * @return Liste mit Koordinaten
     * @throws java.io.IOException
     */
    private static List<WGS84> parseItnFile(final File file) throws IOException {
        List<WGS84> waypoints = new ArrayList<>();

        BufferedReader br = new BufferedReader(new FileReader(file));
        String line;

        do {
            line = br.readLine();
            if (!line.startsWith("<")) {
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
