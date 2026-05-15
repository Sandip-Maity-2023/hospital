# Hospital Management System

This repository now includes a minimal **frontend + backend** hospital management system.

## Features
- Patient management (create/list)
- Doctor management (create/list)
- Appointment management (create/list)
- Browser UI powered by API endpoints

## Project Structure
- Backend: `src/medical/backend/HospitalBackendServer.java`
- Frontend: `src/frontend/index.html`, `src/frontend/app.js`, `src/frontend/styles.css`

## Run
From the repository root:

```bash
javac src/medical/backend/HospitalBackendServer.java
java -cp src medical.backend.HospitalBackendServer
```

Open `http://localhost:8080` in your browser.

## API Endpoints
- `GET /api/patients`
- `POST /api/patients`
- `GET /api/doctors`
- `POST /api/doctors`
- `GET /api/appointments`
- `POST /api/appointments`
