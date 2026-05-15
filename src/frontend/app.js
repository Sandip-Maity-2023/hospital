const api = {
  patients: "/api/patients",
  doctors: "/api/doctors",
  appointments: "/api/appointments"
};

const statusEl = document.getElementById("status");
const patientForm = document.getElementById("patient-form");
const doctorForm = document.getElementById("doctor-form");
const appointmentForm = document.getElementById("appointment-form");

async function request(url, options = {}) {
  const response = await fetch(url, {
    headers: { "Content-Type": "application/json" },
    ...options
  });

  if (!response.ok) {
    let message = "Request failed";
    try {
      const body = await response.json();
      message = body.error || message;
    } catch (_) {
    }
    throw new Error(message);
  }

  return response.status === 204 ? {} : response.json();
}

function setStatus(text, isError = false) {
  statusEl.textContent = text;
  statusEl.style.color = isError ? "#b00020" : "#006400";
}

function setOptions(selectEl, items, labelBuilder) {
  selectEl.innerHTML = "";
  for (const item of items) {
    const option = document.createElement("option");
    option.value = item.id;
    option.textContent = labelBuilder(item);
    selectEl.appendChild(option);
  }
}

function renderRows(targetId, rows) {
  const tbody = document.getElementById(targetId);
  tbody.innerHTML = "";
  for (const row of rows) {
    const tr = document.createElement("tr");
    for (const cell of row) {
      const td = document.createElement("td");
      td.textContent = String(cell);
      tr.appendChild(td);
    }
    tbody.appendChild(tr);
  }
}

async function loadData() {
  const [{ patients }, { doctors }, { appointments }] = await Promise.all([
    request(api.patients),
    request(api.doctors),
    request(api.appointments)
  ]);

  setOptions(
    document.getElementById("appointment-patient"),
    patients,
    (patient) => `${patient.id} - ${patient.name}`
  );

  setOptions(
    document.getElementById("appointment-doctor"),
    doctors,
    (doctor) => `${doctor.id} - ${doctor.name} (${doctor.specialization})`
  );

  renderRows(
    "patients-table",
    patients.map((patient) => [patient.id, patient.name, patient.age, patient.illness])
  );

  renderRows(
    "doctors-table",
    doctors.map((doctor) => [doctor.id, doctor.name, doctor.specialization])
  );

  renderRows(
    "appointments-table",
    appointments.map((appointment) => [
      appointment.id,
      appointment.patientId,
      appointment.doctorId,
      appointment.dateTime
    ])
  );
}

patientForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  try {
    await request(api.patients, {
      method: "POST",
      body: JSON.stringify({
        name: document.getElementById("patient-name").value,
        age: document.getElementById("patient-age").value,
        illness: document.getElementById("patient-illness").value
      })
    });
    patientForm.reset();
    await loadData();
    setStatus("Patient saved");
  } catch (error) {
    setStatus(error.message, true);
  }
});

doctorForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  try {
    await request(api.doctors, {
      method: "POST",
      body: JSON.stringify({
        name: document.getElementById("doctor-name").value,
        specialization: document.getElementById("doctor-specialization").value
      })
    });
    doctorForm.reset();
    await loadData();
    setStatus("Doctor saved");
  } catch (error) {
    setStatus(error.message, true);
  }
});

appointmentForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  try {
    await request(api.appointments, {
      method: "POST",
      body: JSON.stringify({
        patientId: document.getElementById("appointment-patient").value,
        doctorId: document.getElementById("appointment-doctor").value,
        dateTime: document.getElementById("appointment-date").value
      })
    });
    appointmentForm.reset();
    await loadData();
    setStatus("Appointment created");
  } catch (error) {
    setStatus(error.message, true);
  }
});

loadData().catch((error) => {
  setStatus(error.message, true);
});
