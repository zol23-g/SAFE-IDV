# SAFE IDV Demo (Java 11 / Spring Boot 2.7.18)

Extracted and decompiled Maven project from `safe-idv-demo-java11-external-config-token-fix.jar`.

## Project Structure
- `src/main/java/com/safe/idv/` - Application source code
  - `SafeIdvApplication.java` - Spring Boot main application entry point
  - `controller/` - REST controllers (`CaseController`, `IdScanController`, `ReportController`, `HealthController`)
  - `service/` - Business logic (`LiveAnalysisOrchestrator`, `VisionProviderService`, `ComparisonService`, `BarcodeDecodeService`, etc.)
  - `config/` - Configuration classes (`ApiKeyService`, `ServiceProviderConfig`, `WebConfig`)
  - `model/` - Request/response DTOs and models
  - `barcode/` - AAMVA and Code39 barcode parsing services
- `src/main/resources/`
  - `application.properties` - Server configuration and environment variable bindings
  - `static/` - HTML demo capture and report pages
- `pom.xml` - Maven project descriptor (Spring Boot 2.7.18, Java 11)

## Running the Application

### Option 1: In IntelliJ IDEA
1. Open the project folder `C:\Users\zelalem.wubet\projects\personal\safe-idv-demo-java11-external-config-token-fix` in IntelliJ.
2. Ensure Project SDK is set to JDK 11 (e.g. Temurin 11 in `File > Project Structure > Project`).
3. Locate `src/main/java/com/safe/idv/SafeIdvApplication.java`.
4. Right-click and choose **Run 'SafeIdvApplication'** (or click the green Run button next to `main()`).

### Option 2: Using Maven Wrapper in Terminal
```powershell
.\mvnw.cmd spring-boot:run
```

### Option 3: Package and Run JAR
```powershell
.\mvnw.cmd clean package -DskipTests
java -jar target/safe-idv-demo.jar
```

## Endpoints
- Port: `http://localhost:8080` (configured in `application.properties`)
- Health Check: `GET /actuator/health` or `GET /api/v1/health`
- Saved Reports: `GET /reports/index.html`
- IDScan Submit: `POST /api/v1/idscan/submit`
