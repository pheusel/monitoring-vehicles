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
import java.util.regex.Pattern;

public class Main {

    public static void main(String[] args) throws Exception {

        int qos = 0;

        // Get vehicleId
        String vehicleId = Utils.askInput("Beliebige Fahrzeug-ID", "postauto");

        // Get route to be driven
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

        // Get MQTT-Broker address
        String mqttAddress = Utils.askInput("MQTT-Broker", Utils.MQTT_BROKER_ADDRESS);

        MqttClient client = new MqttClient(mqttAddress, MqttClient.generateClientId());

        // Send LWT message when connection is aborted
        StatusMessage lwt = new StatusMessage();
        lwt.type = StatusType.CONNECTION_LOST;
        lwt.vehicleId = vehicleId;
        lwt.message = "Last Will and Testament";

        MqttConnectOptions conOp = new MqttConnectOptions();
        conOp.setCleanSession(true);
        conOp.setWill(Utils.MQTT_TOPIC_NAME, lwt.toJson(), qos, true);
        System.out.println("Connecting to broker: " + mqttAddress);

        // Connect to MQTT-Broker

        client.connect(conOp);
        System.out.println("Connected");

        // Send status message
        StatusMessage statusMessage = new StatusMessage();
        statusMessage.type = StatusType.VEHICLE_READY;
        statusMessage.vehicleId = vehicleId;
        statusMessage.message = "Register Vehicle";

        MqttMessage message = new MqttMessage(statusMessage.toJson());
        message.setQos(qos);
        client.publish(Utils.MQTT_TOPIC_NAME, message);
        System.out.println("Message was published");

        // thread, which determines and sends the current sensor values of the vehicle every second.
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

        // Wait until the program is to be terminated
        Utils.fromKeyboard.readLine();

        vehicle.stopVehicle();

        timer.cancel();

        System.out.println("Vehicle data was published");


        // Sending LWT Messages Manually
        client.publish(Utils.MQTT_TOPIC_NAME, new MqttMessage(lwt.toJson()));
        client.disconnect();
        System.out.println("Disconnected");
        System.exit(0);
    }

    private static List<WGS84> parseItnFile(final File file) throws IOException {
        List<WGS84> waypoints = new ArrayList<>();

        BufferedReader br = new BufferedReader(new FileReader(file));
        String line = br.readLine();

        while (line != null) {
            if (!line.startsWith(Pattern.quote("<"))) {
                String[] as = line.split(Pattern.quote("|"));
                WGS84 wgs84 = new WGS84();
                wgs84.longitude = Double.valueOf(as[0]) / 100000.0;
                wgs84.latitude = Double.valueOf(as[1]) / 100000.0;
                waypoints.add(wgs84);
            }
            line = br.readLine();
        }
        return waypoints;
    }
}
