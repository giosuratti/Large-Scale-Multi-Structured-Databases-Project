import pandas as pd
from faker import Faker
import random
import json
import uuid
from datetime import datetime, timedelta

# --- 1. CONFIGURATION ---
fake = Faker('en_US')
NUM_PATIENTS = 100
NUM_ADMINS = 5

# Real Data File Paths
FILE_DOCTORS = "doctors_unique.csv"
FILE_DISEASES = "nodes_disease.csv"
FILE_SYMPTOMS = "nodes_symptom.csv"
FILE_SPECIALIZATIONS = "nodes_specialization.csv"

def load_real_data():
    try:
        # Load Doctors
        df_docs = pd.read_csv(FILE_DOCTORS, dtype=str).fillna("")
        # Load clinical entities (assuming 'name' is the column)
        list_diseases = pd.read_csv(FILE_DISEASES)['name'].tolist()
        list_symptoms = pd.read_csv(FILE_SYMPTOMS)['name'].tolist()
        return df_docs, list_symptoms, list_diseases
    except Exception as e:
        print(f"Error loading CSV files: {e}. Ensure filenames match.")
        return pd.DataFrame(), ["Fever"], ["Common Cold"]

df_docs_real, list_symptoms, list_diseases = load_real_data()

def json_serial(obj):
    if isinstance(obj, datetime): return obj.isoformat()
    raise TypeError("Type not serializable")

# --- 2. GENERATION LOGIC ---

admins = []
patients = []
doctors_coll = []
appointments_coll = []
symptom_reports_coll = []

# A. DOCTORS (Based on Real NPI Data)
print("Processing real doctor data...")
for _, row in df_docs_real.iterrows():
    doctors_coll.append({
        "_id": str(row['NPI']), # NPI is the Bridge to Neo4j
        "email": fake.email(),
        "password": "hashed_password_123",
        "telephone": row.get('Telephone Number', fake.phone_number()),
        "first_name": row['Provider First Name'].title(),
        "last_name": row['Provider Last Name'].title(),
        "specialties": [row['pri_spec'].title()],
        "location": row['City/Town'].title(),
        "available_slots": ["09:00", "10:00", "11:00", "15:00", "16:00"],
        "booked_today": [],
        "avg_rating": 0.0,
        "rating_count": 0,
        "total_appointments": 0
    })

# B. ADMINS
for _ in range(NUM_ADMINS):
    admins.append({
        "id": str(uuid.uuid4()),
        "email": fake.email(),
        "password": "hashed_admin_pass",
        "telephone": fake.phone_number()
    })

# C. PATIENTS & INTERACTIONS (Coherent Generation)
print(f"Generating {NUM_PATIENTS} patients and consistent interactions...")
cities = df_docs_real['City/Town'].unique().tolist() if not df_docs_real.empty else ["New York"]

for i in range(NUM_PATIENTS):
    p_id = f"PAT_{i:04d}"
    p_fn, p_ln = fake.first_name(), fake.last_name()
    p_tel = fake.phone_number()
    p_gender = random.choice(["M", "F", "Other"])
    p_age = random.randint(18, 90)
    p_loc = random.choice(cities)

    p_reports_embedded = []
    p_appts_embedded = []

    # Generate Symptom Reports
    for r in range(random.randint(1, 3)):
        sr_id = str(uuid.uuid4())
        sr_date = fake.date_time_between(start_date='-60d', end_date='now')
        
        report = {
            "id": sr_id,
            "context": fake.sentence(),
            "symptoms": random.sample(list_symptoms, k=random.randint(1, 3)),
            "possible_diagnosies": random.sample(list_diseases, k=2), # UML Spelling
            "patient_age": p_age,
            "patient_gender": p_gender,
            "patient_location": p_loc,
            "created_at": sr_date
        }
        symptom_reports_coll.append(report)
        # Denormalization into Patient
        p_reports_embedded.append({"id": sr_id, "symptoms": report["symptoms"], "date": sr_date})

    # Generate Appointments & Ratings
    for _ in range(random.randint(1, 4)):
        doc = random.choice(doctors_coll)
        apt_date = fake.date_time_between(start_date='-30d', end_date='+30d')
        is_past = apt_date < datetime.now()
        
        rating_val = random.randint(3, 5) if is_past else None
        
        appt = {
            "doctor_id": doc["_id"],
            "patient_last_name": p_ln,
            "patient_first_name": p_fn,
            "location": doc["location"],
            "status": "Completed" if is_past else "Scheduled",
            "doctor_rating": rating_val,
            "patient_telephone": p_tel,
            "doctor_specialties": doc["specialties"],
            "datetime": apt_date
        }
        appointments_coll.append(appt)
        
        # Update Doctor Stats (Consistency)
        doc["total_appointments"] += 1
        if is_past and rating_val:
            old_score = doc["avg_rating"] * doc["rating_count"]
            doc["rating_count"] += 1
            doc["avg_rating"] = round((old_score + rating_val) / doc["rating_count"], 1)
        elif not is_past:
            doc["booked_today"].append(apt_date.strftime("%H:%M"))

        # Denormalization into Patient
        p_appts_embedded.append({"doctor": doc["last_name"], "date": apt_date, "status": appt["status"]})

    # Final Patient Document
    patients.append({
        "id": p_id,
        "email": fake.email(),
        "password": "hashed_patient_pass",
        "telephone": p_tel,
        "first_name": p_fn,
        "last_name": p_ln,
        "gender": p_gender,
        "age": p_age,
        "location": p_loc,
        "ratings": [a["doctor_rating"] for a in appointments_coll if a["patient_last_name"] == p_ln and a["doctor_rating"] is not None],
        "booked_appointments": p_appts_embedded,
        "recent_symptom_reports": p_reports_embedded
    })

# --- 3. EXPORT TO JSON ---
output_files = [
    (admins, "mongo_admins.json"),
    (doctors_coll, "mongo_doctors.json"),
    (patients, "mongo_patients.json"),
    (appointments_coll, "mongo_appointments.json"),
    (symptom_reports_coll, "mongo_symptom_reports.json")
]

for data, filename in output_files:
    with open(filename, 'w', encoding='utf-8') as f:
        json.dump(data, f, default=json_serial, indent=2, ensure_ascii=False)

print(f"✅ Success! Files generated: {len(doctors_coll)} doctors and {len(patients)} patients with consistent ratings.")