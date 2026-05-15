package medical.backend;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HospitalBackendServer {

    private static final int PORT = 8080;
    private static final List<Patient> PATIENTS = Collections.synchronizedList(new ArrayList<>());
    private static final List<Doctor> DOCTORS = Collections.synchronizedList(new ArrayList<>());
    private static final List<Appointment> APPOINTMENTS = Collections.synchronizedList(new ArrayList<>());
    private static final AtomicInteger PATIENT_ID = new AtomicInteger(1);
    private static final AtomicInteger DOCTOR_ID = new AtomicInteger(1);
    private static final AtomicInteger APPOINTMENT_ID = new AtomicInteger(1);

    public static void main(String[] args) throws IOException {
        seedData();

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/", new StaticFileHandler());
        server.createContext("/api/patients", new PatientsHandler());
        server.createContext("/api/doctors", new DoctorsHandler());
        server.createContext("/api/appointments", new AppointmentsHandler());

        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();

        System.out.println("Hospital backend running at http://localhost:" + PORT);
    }

    private static void seedData() {
        if (!PATIENTS.isEmpty() || !DOCTORS.isEmpty()) {
            return;
        }

        Patient patient = new Patient(PATIENT_ID.getAndIncrement(), "Amit Das", 34, "Fever");
        Doctor doctor = new Doctor(DOCTOR_ID.getAndIncrement(), "Dr. Roy", "General Medicine");
        PATIENTS.add(patient);
        DOCTORS.add(doctor);
        APPOINTMENTS.add(new Appointment(
                APPOINTMENT_ID.getAndIncrement(),
                patient.id,
                doctor.id,
                LocalDateTime.now().plusDays(1).toString()
        ));
    }

    private static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            if ("/".equals(path)) {
                path = "/index.html";
            }

            if (!"/index.html".equals(path) && !"/app.js".equals(path) && !"/styles.css".equals(path)) {
                sendJson(exchange, 404, "{\"error\":\"Not found\"}");
                return;
            }

            Path filePath = Path.of("src", "frontend", path.substring(1));
            if (!Files.exists(filePath)) {
                sendJson(exchange, 404, "{\"error\":\"Frontend asset missing\"}");
                return;
            }

            byte[] bytes = Files.readAllBytes(filePath);
            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", contentType(path));
            headers.set("Cache-Control", "no-store");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }

        private String contentType(String path) {
            if (path.endsWith(".html")) {
                return "text/html; charset=utf-8";
            }
            if (path.endsWith(".js")) {
                return "application/javascript; charset=utf-8";
            }
            if (path.endsWith(".css")) {
                return "text/css; charset=utf-8";
            }
            return "text/plain; charset=utf-8";
        }
    }

    private static class PatientsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();

            if ("GET".equalsIgnoreCase(method)) {
                sendJson(exchange, 200, "{\"patients\":" + patientsAsJson() + "}");
                return;
            }

            if ("POST".equalsIgnoreCase(method)) {
                String body = readBody(exchange.getRequestBody());
                String name = extractField(body, "name");
                String ageText = extractField(body, "age");
                String illness = extractField(body, "illness");

                if (isBlank(name) || isBlank(ageText) || isBlank(illness)) {
                    sendJson(exchange, 400, "{\"error\":\"name, age and illness are required\"}");
                    return;
                }

                int age;
                try {
                    age = Integer.parseInt(ageText.trim());
                } catch (NumberFormatException ex) {
                    sendJson(exchange, 400, "{\"error\":\"age must be a valid number\"}");
                    return;
                }

                Patient patient = new Patient(PATIENT_ID.getAndIncrement(), name.trim(), age, illness.trim());
                PATIENTS.add(patient);
                sendJson(exchange, 201, "{\"patient\":" + patient.toJson() + "}");
                return;
            }

            sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
        }
    }

    private static class DoctorsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();

            if ("GET".equalsIgnoreCase(method)) {
                sendJson(exchange, 200, "{\"doctors\":" + doctorsAsJson() + "}");
                return;
            }

            if ("POST".equalsIgnoreCase(method)) {
                String body = readBody(exchange.getRequestBody());
                String name = extractField(body, "name");
                String specialization = extractField(body, "specialization");

                if (isBlank(name) || isBlank(specialization)) {
                    sendJson(exchange, 400, "{\"error\":\"name and specialization are required\"}");
                    return;
                }

                Doctor doctor = new Doctor(DOCTOR_ID.getAndIncrement(), name.trim(), specialization.trim());
                DOCTORS.add(doctor);
                sendJson(exchange, 201, "{\"doctor\":" + doctor.toJson() + "}");
                return;
            }

            sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
        }
    }

    private static class AppointmentsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();

            if ("GET".equalsIgnoreCase(method)) {
                sendJson(exchange, 200, "{\"appointments\":" + appointmentsAsJson() + "}");
                return;
            }

            if ("POST".equalsIgnoreCase(method)) {
                String body = readBody(exchange.getRequestBody());
                String patientIdText = extractField(body, "patientId");
                String doctorIdText = extractField(body, "doctorId");
                String dateTime = extractField(body, "dateTime");

                if (isBlank(patientIdText) || isBlank(doctorIdText) || isBlank(dateTime)) {
                    sendJson(exchange, 400, "{\"error\":\"patientId, doctorId and dateTime are required\"}");
                    return;
                }

                int patientId;
                int doctorId;
                try {
                    patientId = Integer.parseInt(patientIdText.trim());
                    doctorId = Integer.parseInt(doctorIdText.trim());
                } catch (NumberFormatException ex) {
                    sendJson(exchange, 400, "{\"error\":\"patientId and doctorId must be numbers\"}");
                    return;
                }

                if (!patientExists(patientId) || !doctorExists(doctorId)) {
                    sendJson(exchange, 400, "{\"error\":\"Referenced patient or doctor does not exist\"}");
                    return;
                }

                Appointment appointment = new Appointment(
                        APPOINTMENT_ID.getAndIncrement(),
                        patientId,
                        doctorId,
                        dateTime.trim()
                );
                APPOINTMENTS.add(appointment);
                sendJson(exchange, 201, "{\"appointment\":" + appointment.toJson() + "}");
                return;
            }

            sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
        }
    }

    private static boolean patientExists(int patientId) {
        synchronized (PATIENTS) {
            return PATIENTS.stream().anyMatch(patient -> patient.id == patientId);
        }
    }

    private static boolean doctorExists(int doctorId) {
        synchronized (DOCTORS) {
            return DOCTORS.stream().anyMatch(doctor -> doctor.id == doctorId);
        }
    }

    private static String patientsAsJson() {
        synchronized (PATIENTS) {
            List<String> items = new ArrayList<>();
            for (Patient patient : PATIENTS) {
                items.add(patient.toJson());
            }
            return listToJson(items);
        }
    }

    private static String doctorsAsJson() {
        synchronized (DOCTORS) {
            List<String> items = new ArrayList<>();
            for (Doctor doctor : DOCTORS) {
                items.add(doctor.toJson());
            }
            return listToJson(items);
        }
    }

    private static String appointmentsAsJson() {
        synchronized (APPOINTMENTS) {
            List<String> items = new ArrayList<>();
            for (Appointment appointment : APPOINTMENTS) {
                items.add(appointment.toJson());
            }
            return listToJson(items);
        }
    }

    private static String listToJson(List<String> items) {
        return "[" + String.join(",", items) + "]";
    }

    private static String readBody(InputStream body) throws IOException {
        return new String(body.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static String extractField(String json, String fieldName) {
        if (json == null) {
            return "";
        }
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(fieldName) + "\\\"\\s*:\\s*(\\\"(.*?)\\\"|[-0-9]+)");
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            return "";
        }
        String quotedValue = matcher.group(2);
        if (quotedValue != null) {
            return quotedValue;
        }
        String fullValue = matcher.group(1);
        return fullValue == null ? "" : fullValue;
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static void sendJson(HttpExchange exchange, int status, String responseBody) throws IOException {
        byte[] payload = responseBody.getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type");

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(payload);
        }
    }

    private static class Patient {
        private final int id;
        private final String name;
        private final int age;
        private final String illness;

        private Patient(int id, String name, int age, String illness) {
            this.id = id;
            this.name = name;
            this.age = age;
            this.illness = illness;
        }

        private String toJson() {
            return "{\"id\":" + id +
                    ",\"name\":\"" + escapeJson(name) + "\"" +
                    ",\"age\":" + age +
                    ",\"illness\":\"" + escapeJson(illness) + "\"}";
        }
    }

    private static class Doctor {
        private final int id;
        private final String name;
        private final String specialization;

        private Doctor(int id, String name, String specialization) {
            this.id = id;
            this.name = name;
            this.specialization = specialization;
        }

        private String toJson() {
            return "{\"id\":" + id +
                    ",\"name\":\"" + escapeJson(name) + "\"" +
                    ",\"specialization\":\"" + escapeJson(specialization) + "\"}";
        }
    }

    private static class Appointment {
        private final int id;
        private final int patientId;
        private final int doctorId;
        private final String dateTime;

        private Appointment(int id, int patientId, int doctorId, String dateTime) {
            this.id = id;
            this.patientId = patientId;
            this.doctorId = doctorId;
            this.dateTime = dateTime;
        }

        private String toJson() {
            return "{\"id\":" + id +
                    ",\"patientId\":" + patientId +
                    ",\"doctorId\":" + doctorId +
                    ",\"dateTime\":\"" + escapeJson(dateTime) + "\"}";
        }
    }
}
