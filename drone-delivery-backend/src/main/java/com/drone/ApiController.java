package com.drone;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ApiController {
    private static final List<String> DRONE = List.of("Available", "In Use", "Maintenance");
    private static final List<String> ORDER = List.of("Placed", "Assigned", "In Transit", "Delivered", "Cancelled");
    private static final List<String> PAY = List.of("Pending", "Paid", "Failed", "Refunded");
    private static final List<String> ASSIGN = List.of("Assigned", "In Transit", "Completed", "Cancelled");

    private final JdbcTemplate db;

    public ApiController(JdbcTemplate db) {
        this.db = db;
    }

    // ---------- helpers ----------
    static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
    static ResponseStatusException notFound(String m) { return new ResponseStatusException(HttpStatus.NOT_FOUND, m); }

    static String need(Map<String, Object> b, String k) {
        Object v = b.get(k);
        if (v == null || v.toString().trim().isEmpty()) throw bad("Missing field: " + k);
        return v.toString().trim();
    }

    static String opt(Map<String, Object> b, String k) {
        Object v = b.get(k);
        return (v == null || v.toString().trim().isEmpty()) ? null : v.toString().trim();
    }

    static BigDecimal num(Map<String, Object> b, String k) {
        try { return new BigDecimal(need(b, k)); } catch (NumberFormatException e) { throw bad("Invalid number: " + k); }
    }

    static long id(Map<String, Object> b, String k) {
        try { return Long.parseLong(need(b, k)); } catch (NumberFormatException e) { throw bad("Invalid id: " + k); }
    }

    static LocalDate date(Map<String, Object> b, String k) {
        try { return LocalDate.parse(need(b, k)); } catch (DateTimeParseException e) { throw bad("Invalid date (use YYYY-MM-DD): " + k); }
    }

    static void oneOf(String v, List<String> allowed) {
        if (!allowed.contains(v)) throw bad("Status must be one of " + String.join(", ", allowed));
    }

    private void require(String table, String col, long id, String label) {
        Integer n = db.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + col + "=?", Integer.class, id);
        if (n == null || n == 0) throw notFound(label + " " + id + " not found");
    }

    private long insert(String sql, Object... args) {
        KeyHolder kh = new GeneratedKeyHolder();
        db.update(c -> {
            PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            return ps;
        }, kh);
        Number k = kh.getKey();
        return k == null ? 0 : k.longValue();
    }

    private Map<String, Object> setStatus(String table, String idCol, String col, long id, Map<String, Object> b, List<String> allowed, String label) {
        String st = need(b, "status");
        oneOf(st, allowed);
        require(table, idCol, id, label);
        db.update("UPDATE " + table + " SET " + col + "=? WHERE " + idCol + "=?", st, id);
        return Map.of("id", id, "status", st);
    }

    // ---------- customers ----------
    @GetMapping("/customers")
    public List<Map<String, Object>> customers() {
        return db.queryForList("SELECT * FROM Customer ORDER BY customer_id");
    }

    @PostMapping("/customers")
    public Map<String, Object> addCustomer(@RequestBody Map<String, Object> b) {
        long id = insert("INSERT INTO Customer (name,phone,email,address) VALUES (?,?,?,?)",
                need(b, "name"), need(b, "phone"), opt(b, "email"), opt(b, "address"));
        return Map.of("id", id);
    }

    // ---------- drones ----------
    @GetMapping("/drones")
    public List<Map<String, Object>> drones() {
        return db.queryForList("SELECT * FROM Drone ORDER BY drone_id");
    }

    @PostMapping("/drones")
    public Map<String, Object> addDrone(@RequestBody Map<String, Object> b) {
        String st = opt(b, "status") == null ? "Available" : opt(b, "status");
        oneOf(st, DRONE);
        long id = insert("INSERT INTO Drone (model,payload_capacity,status) VALUES (?,?,?)", need(b, "model"), num(b, "payload_capacity"), st);
        return Map.of("id", id);
    }

    @PutMapping("/drones/{id}/status")
    public Map<String, Object> droneStatus(@PathVariable long id, @RequestBody Map<String, Object> b) {
        return setStatus("Drone", "drone_id", "status", id, b, DRONE, "Drone");
    }

    // ---------- pilots ----------
    @GetMapping("/pilots")
    public List<Map<String, Object>> pilots() {
        return db.queryForList("SELECT p.*, IF(certification_expiry >= CURDATE(), 1, 0) AS certified FROM Pilot p ORDER BY pilot_id");
    }

    @PostMapping("/pilots")
    public Map<String, Object> addPilot(@RequestBody Map<String, Object> b) {
        long id = insert("INSERT INTO Pilot (name,phone,certification_expiry) VALUES (?,?,?)", need(b, "name"), need(b, "phone"), date(b, "certification_expiry"));
        return Map.of("id", id);
    }

    // ---------- orders & packages ----------
    @GetMapping("/orders")
    public List<Map<String, Object>> orders() {
        return db.queryForList(
            "SELECT o.*, c.name AS customer_name, " +
            "(SELECT COUNT(*) FROM OrderPackage p WHERE p.order_id=o.order_id) AS package_count, " +
            "(SELECT payment_status FROM Payment y WHERE y.order_id=o.order_id LIMIT 1) AS payment_status " +
            "FROM DeliveryOrder o JOIN Customer c ON c.customer_id=o.customer_id ORDER BY o.order_id");
    }

    @PostMapping("/orders")
    public Map<String, Object> addOrder(@RequestBody Map<String, Object> b) {
        long cid = id(b, "customer_id");
        require("Customer", "customer_id", cid, "Customer");
        long id = insert("INSERT INTO DeliveryOrder (customer_id,pickup_address,delivery_address,delivery_charge,status) VALUES (?,?,?,?, 'Placed')",
                cid, need(b, "pickup_address"), need(b, "delivery_address"), num(b, "delivery_charge"));
        return Map.of("id", id);
    }

    @PutMapping("/orders/{id}/status")
    public Map<String, Object> orderStatus(@PathVariable long id, @RequestBody Map<String, Object> b) {
        return setStatus("DeliveryOrder", "order_id", "status", id, b, ORDER, "DeliveryOrder");
    }

    @GetMapping("/orders/{id}/packages")
    public List<Map<String, Object>> orderPackages(@PathVariable long id) {
        require("DeliveryOrder", "order_id", id, "DeliveryOrder");
        return db.queryForList("SELECT * FROM OrderPackage WHERE order_id=? ORDER BY package_no", id);
    }

    @PostMapping("/orders/{id}/packages")
    public Map<String, Object> addPackage(@PathVariable long id, @RequestBody Map<String, Object> b) {
        require("DeliveryOrder", "order_id", id, "DeliveryOrder");
        Integer next = db.queryForObject("SELECT COALESCE(MAX(package_no),0)+1 FROM OrderPackage WHERE order_id=?", Integer.class, id);
        db.update("INSERT INTO OrderPackage (order_id,package_no,description,weight_kg) VALUES (?,?,?,?)", id, next, opt(b, "description"), num(b, "weight_kg"));
        return Map.of("order_id", id, "package_no", next);
    }

    @GetMapping("/packages")
    public List<Map<String, Object>> packages(@RequestParam(name = "min_weight", defaultValue = "0") BigDecimal min) {
        return db.queryForList("SELECT * FROM OrderPackage WHERE weight_kg > ? ORDER BY order_id, package_no", min);
    }

    // ---------- payments ----------
    @GetMapping("/payments")
    public List<Map<String, Object>> payments() {
        return db.queryForList(
            "SELECT y.*, c.name AS customer_name FROM Payment y " +
            "JOIN DeliveryOrder o ON o.order_id=y.order_id JOIN Customer c ON c.customer_id=o.customer_id ORDER BY y.payment_id");
    }

    @PostMapping("/payments")
    public Map<String, Object> addPayment(@RequestBody Map<String, Object> b) {
        long oid = id(b, "order_id");
        require("DeliveryOrder", "order_id", oid, "DeliveryOrder");
        Integer has = db.queryForObject("SELECT COUNT(*) FROM Payment WHERE order_id=?", Integer.class, oid);
        if (has != null && has > 0) throw bad("This order already has a payment");
        String st = opt(b, "payment_status") == null ? "Pending" : opt(b, "payment_status");
        oneOf(st, PAY);
        long id = insert("INSERT INTO Payment (order_id,amount,payment_method,payment_status) VALUES (?,?,?,?)", oid, num(b, "amount"), need(b, "payment_method"), st);
        return Map.of("id", id);
    }

    @PutMapping("/payments/{id}/status")
    public Map<String, Object> paymentStatus(@PathVariable long id, @RequestBody Map<String, Object> b) {
        return setStatus("Payment", "payment_id", "payment_status", id, b, PAY, "Payment");
    }

    // ---------- assignments ----------
    @GetMapping("/assignments")
    public List<Map<String, Object>> assignments() {
        return db.queryForList(
            "SELECT a.assignment_id, a.order_id, d.model AS drone_model, p.name AS pilot_name, a.status " +
            "FROM DeliveryAssignment a JOIN Drone d ON d.drone_id=a.drone_id JOIN Pilot p ON p.pilot_id=a.pilot_id ORDER BY a.assignment_id");
    }

    @PostMapping("/assignments")
    public Map<String, Object> addAssignment(@RequestBody Map<String, Object> b) {
        long oid = id(b, "order_id"), did = id(b, "drone_id"), pid = id(b, "pilot_id");
        require("DeliveryOrder", "order_id", oid, "DeliveryOrder");
        require("Drone", "drone_id", did, "Drone");
        require("Pilot", "pilot_id", pid, "Pilot");
        String ds = db.queryForObject("SELECT status FROM Drone WHERE drone_id=?", String.class, did);
        if (!"Available".equals(ds)) throw bad("Drone is not available (" + ds + ")");
        Integer valid = db.queryForObject("SELECT COUNT(*) FROM Pilot WHERE pilot_id=? AND certification_expiry >= CURDATE()", Integer.class, pid);
        if (valid == null || valid == 0) throw bad("Pilot certification has expired");
        Integer active = db.queryForObject("SELECT COUNT(*) FROM DeliveryAssignment WHERE order_id=? AND status IN ('Assigned','In Transit')", Integer.class, oid);
        if (active != null && active > 0) throw bad("Order already has an active assignment");
        long id = insert("INSERT INTO DeliveryAssignment (order_id,drone_id,pilot_id,status) VALUES (?,?,?, 'Assigned')", oid, did, pid);
        return Map.of("id", id);
    }

    @PutMapping("/assignments/{id}/status")
    public Map<String, Object> assignmentStatus(@PathVariable long id, @RequestBody Map<String, Object> b) {
        return setStatus("DeliveryAssignment", "assignment_id", "status", id, b, ASSIGN, "DeliveryAssignment");
    }

    // ---------- maintenance ----------
    @GetMapping("/maintenance")
    public List<Map<String, Object>> maintenance() {
        return db.queryForList(
            "SELECT m.*, d.model AS drone_model FROM Maintenance m JOIN Drone d ON d.drone_id=m.drone_id ORDER BY m.service_date DESC, m.maintenance_id DESC");
    }

    @PostMapping("/maintenance")
    public Map<String, Object> addMaintenance(@RequestBody Map<String, Object> b) {
        long did = id(b, "drone_id");
        require("Drone", "drone_id", did, "Drone");
        BigDecimal cost = opt(b, "cost") == null ? null : num(b, "cost");
        long id = insert("INSERT INTO Maintenance (drone_id,service_date,description,cost) VALUES (?,?,?,?)", did, date(b, "service_date"), opt(b, "description"), cost);
        return Map.of("id", id);
    }

    // ---------- overview ----------
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total_revenue", db.queryForObject("SELECT COALESCE(SUM(amount),0) FROM Payment WHERE payment_status='Paid'", BigDecimal.class));
        m.put("total_orders", db.queryForObject("SELECT COUNT(*) FROM DeliveryOrder", Integer.class));
        m.put("available_drones", db.queryForObject("SELECT COUNT(*) FROM Drone WHERE status='Available'", Integer.class));
        m.put("orders_per_customer", db.queryForList("SELECT c.name, COUNT(o.order_id) AS total_orders FROM Customer c LEFT JOIN DeliveryOrder o ON c.customer_id=o.customer_id GROUP BY c.customer_id, c.name"));
        m.put("avg_weight_per_order", db.queryForList("SELECT order_id, AVG(weight_kg) AS average_weight FROM OrderPackage GROUP BY order_id"));
        m.put("drones_above_avg_payload", db.queryForList("SELECT drone_id, model, payload_capacity FROM Drone WHERE payload_capacity > (SELECT AVG(payload_capacity) FROM Drone)"));
        return m;
    }
}
