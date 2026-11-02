CREATE TABLE IF NOT EXISTS Customer (
  customer_id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  phone VARCHAR(20) NOT NULL UNIQUE,
  email VARCHAR(100),
  address VARCHAR(200)
);
CREATE TABLE IF NOT EXISTS Drone (
  drone_id INT AUTO_INCREMENT PRIMARY KEY,
  model VARCHAR(100) NOT NULL,
  payload_capacity DECIMAL(6,2) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'Available'
);
CREATE TABLE IF NOT EXISTS Pilot (
  pilot_id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  phone VARCHAR(20) NOT NULL UNIQUE,
  certification_expiry DATE NOT NULL
);
CREATE TABLE IF NOT EXISTS DeliveryOrder (
  order_id INT AUTO_INCREMENT PRIMARY KEY,
  customer_id INT NOT NULL,
  pickup_address VARCHAR(200) NOT NULL,
  delivery_address VARCHAR(200) NOT NULL,
  delivery_charge DECIMAL(10,2) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'Placed',
  FOREIGN KEY (customer_id) REFERENCES Customer(customer_id)
);
CREATE TABLE IF NOT EXISTS OrderPackage (
  order_id INT NOT NULL,
  package_no INT NOT NULL,
  description VARCHAR(200),
  weight_kg DECIMAL(6,2) NOT NULL,
  PRIMARY KEY (order_id, package_no),
  FOREIGN KEY (order_id) REFERENCES DeliveryOrder(order_id)
);
CREATE TABLE IF NOT EXISTS Payment (
  payment_id INT AUTO_INCREMENT PRIMARY KEY,
  order_id INT NOT NULL UNIQUE,
  amount DECIMAL(10,2) NOT NULL,
  payment_method VARCHAR(30) NOT NULL,
  payment_status VARCHAR(20) NOT NULL DEFAULT 'Pending',
  FOREIGN KEY (order_id) REFERENCES DeliveryOrder(order_id)
);
CREATE TABLE IF NOT EXISTS DeliveryAssignment (
  assignment_id INT AUTO_INCREMENT PRIMARY KEY,
  order_id INT NOT NULL,
  drone_id INT NOT NULL,
  pilot_id INT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'Assigned',
  FOREIGN KEY (order_id) REFERENCES DeliveryOrder(order_id),
  FOREIGN KEY (drone_id) REFERENCES Drone(drone_id),
  FOREIGN KEY (pilot_id) REFERENCES Pilot(pilot_id)
);
CREATE TABLE IF NOT EXISTS Maintenance (
  maintenance_id INT AUTO_INCREMENT PRIMARY KEY,
  drone_id INT NOT NULL,
  service_date DATE NOT NULL,
  description VARCHAR(200),
  cost DECIMAL(10,2),
  FOREIGN KEY (drone_id) REFERENCES Drone(drone_id)
);
