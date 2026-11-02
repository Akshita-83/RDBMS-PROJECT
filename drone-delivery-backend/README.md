# Drone Delivery Console: backend + website

One Spring Boot app serves both the REST API (`/api/...`) and the website (`/`), so no CORS setup is needed.
On start it creates the tables if missing and loads your sample data (safe to re-run).

## Deploy (about 15 minutes, free tiers)

### 1. Create a hosted MySQL database
Use any MySQL 8 host, for example Aiven (free MySQL), Railway or TiDB Cloud. Note the host, port, database name, user and password.

Your JDBC URL looks like:
`jdbc:mysql://HOST:PORT/DBNAME?sslMode=REQUIRED`

### 2. Put this folder on GitHub
```
git init && git add . && git commit -m "drone delivery"
git branch -M main
git remote add origin https://github.com/YOU/drone-delivery.git
git push -u origin main
```

### 3. Deploy on Render
1. Render dashboard → New → Blueprint (or Web Service → Docker) → pick the repo.
2. Set the three environment variables: `DB_URL`, `DB_USER`, `DB_PASSWORD`.
3. Deploy. Your site will be at `https://drone-delivery.onrender.com` (or similar).

Free Render services sleep after inactivity, so the first load can take about 30 seconds.

## Run locally
```
export DB_URL="jdbc:mysql://localhost:3306/drone_delivery"
export DB_USER=root DB_PASSWORD=yourpassword
mvn spring-boot:run
```
Create the empty `drone_delivery` database first. Then open http://localhost:8080
