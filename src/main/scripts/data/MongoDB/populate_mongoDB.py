import pandas as pd
from faker import Faker
import random
import json
import uuid
import os
import time
import bcrypt
from datetime import datetime, timedelta
from bson import ObjectId

# --- 1. CONFIGURATION ---
fake = Faker('en_US')
NUM_PATIENTS = 500000
NUM_ADMINS = 5

OUTPUT_DIR = "JSONs"
os.makedirs(OUTPUT_DIR, exist_ok=True)

FILE_DOCTORS = "doctors_unique.csv"
FILE_DISEASES = "nodes_disease.csv"
FILE_SYMPTOMS = "nodes_symptom.csv"
FILE_SPECIALIZATIONS = "nodes_specialization.csv"

# Email tracking
used_emails = set()

# Mappa per consistenza geografica (Città -> ZIP reale)
CITY_ZIP_MAP = {}
CITY_STATE_MAP = {}

# --- PASSWORD HASHING ---
def hash_password(plain_text_password):
    salt = bcrypt.gensalt()
    hashed = bcrypt.hashpw(plain_text_password.encode('utf-8'), salt)
    return hashed.decode('utf-8')

print("🔐 Generazione hash sicuri...")
HASH_DOCTOR = hash_password("doc123")
HASH_PATIENT = hash_password("patient123")
HASH_ADMIN = hash_password("admin123")

def load_real_data():
    print("📂 Loading CSV files...")
    try:
        df_docs = pd.read_csv(FILE_DOCTORS, dtype=str).fillna("")
        list_diseases = pd.read_csv(FILE_DISEASES)['name'].tolist()
        list_symptoms = pd.read_csv(FILE_SYMPTOMS)['name'].tolist()
        print(f"   -> Loaded {len(df_docs)} docs, {len(list_diseases)} diseases.")
        return df_docs, list_symptoms, list_diseases
    except Exception as e:
        print(f"❌ Error: {e}. Using dummy data.")
        return pd.DataFrame(), ["Fever", "Cough"], ["Flu", "Cold"]

df_docs_real, list_symptoms, list_diseases = load_real_data()

# --- HELPER FUNCTIONS ---

def json_serial(obj):
    if isinstance(obj, datetime):
        return {"$date": obj.strftime('%Y-%m-%dT%H:%M:%SZ')}
    if isinstance(obj, ObjectId):
        return {"$oid": str(obj)}
    raise TypeError(f"Type {type(obj)} not serializable")

def generate_mongo_object_id():
    return ObjectId()

def generate_unique_email(first_name, last_name, role="user"):
    domains = ["gmail.com", "yahoo.com", "hotmail.com", "outlook.com", "findyourdoc.com", "medical.org"]
    fn_clean = "".join(filter(str.isalnum, first_name.lower()))
    ln_clean = "".join(filter(str.isalnum, last_name.lower()))
    base_email = f"{fn_clean}.{ln_clean}"
    domain = "findyourdoc.com" if role == "admin" else random.choice(domains)
    email = f"{base_email}@{domain}"
    counter = 1
    while email in used_emails:
        email = f"{base_email}{counter}@{domain}"
        counter += 1
    used_emails.add(email)
    return email

def get_weighted_gender():
    return random.choices(['M', 'F', 'Other'], weights=[45, 45, 10], k=1)[0]

def get_random_creation_date(days_back=365):
    return datetime.now() - timedelta(days=random.randint(1, days_back),
                                      hours=random.randint(0, 23),
                                      minutes=random.randint(0, 59))

# --- 2. GENERATION LOGIC ---

admins = []
patients = []
doctors_coll = []
appointments_coll = []
symptom_reports_coll = []

def generate_available_slots():
    potential_slots = []
    start_date = datetime.now().replace(hour=0, minute=0, second=0, microsecond=0)
    for day_offset in range(7):
        current_day = start_date + timedelta(days=day_offset)
        if current_day.weekday() >= 5: continue
        for hour in [9, 10, 11, 14, 15, 16]:
            slot_time = current_day.replace(hour=hour)
            potential_slots.append(slot_time)
    if not potential_slots: return []
    num_slots_to_keep = random.randint(5, min(20, len(potential_slots)))
    return random.sample(potential_slots, k=num_slots_to_keep)

def generate_medical_context(symptoms):
    if not symptoms: return "General malaise reported."
    symptom = symptoms[0].lower()
    days = random.randint(2, 10)
    templates = [
        f"Patient complains of persistent {symptom} for {days} days.",
        f"Sudden onset of {symptom} accompanied by fatigue.",
        f"Recurrent episodes of breathing fast mostly at night.",
        f"Acute pain described as {symptom}, needs checkup.",
        f"Subject reports {symptom} resisting basic treatment."
    ]
    return random.choice(templates)

def is_in_current_week(date_to_check):
    now = datetime.now()
    start_of_week = now - timedelta(days=now.weekday())
    end_of_window = now + timedelta(days=7)
    return now <= date_to_check <= end_of_window

# A. DOCTORS
total_docs = len(df_docs_real)
print(f"\n👨‍⚕️ Generating {total_docs} Doctors...")
start_time_docs = time.time()

for idx, (_, row) in enumerate(df_docs_real.iterrows()):
    if (idx + 1) % 100 == 0:
        print(f"   -> Processed {idx + 1}/{total_docs} doctors...", end='\r')

    first_name = row['Provider First Name'].title()
    last_name = row['Provider Last Name'].title()

    # Estrazione Dati Geografici
    address = row.get('adr_ln_1', fake.street_address())
    city = row.get('City/Town', fake.city()).title()
    state = row.get('State', 'NY')
    zip_code = str(row.get('ZIP Code', '10001'))[:5]

    if city and zip_code:
        CITY_ZIP_MAP[city] = zip_code
        CITY_STATE_MAP[city] = state

    location_obj = {
        "address": address,
        "city": city,
        "state": state,
        "zipCode": zip_code
    }

    doc_specialties = []
    spec1 = row.get('pri_spec', '')
    if spec1: doc_specialties.append(spec1.title())
    spec2 = row.get('sec_spec_1', '')
    if spec2: doc_specialties.append(spec2.title())
    if not doc_specialties: doc_specialties = ["General Practice"]

    doc_oid = generate_mongo_object_id()

    doctors_coll.append({
        "_id": doc_oid,
        "npi": str(row['NPI']),
        "email": generate_unique_email(first_name, last_name, role="doctor"),
        "password": HASH_DOCTOR,
        "telephone": row.get('Telephone Number', fake.phone_number()),
        "firstName": first_name,
        "lastName": last_name,
        "gender": get_weighted_gender(),
        "specialties": doc_specialties,
        "location": location_obj,
        "availableSlots": generate_available_slots(),
        "bookedThisWeek": [],       # Embedded Objects (prossimi 7gg)
        "futureAppointments": [],   # LISTA DI STRINGHE (ID) per appuntamenti > 7gg
        "ratings": [],
        "avgRating": 0.0,
        "ratingCount": 0,
        "totalAppointments": 0,
        "updated": False,
        "createdAt": get_random_creation_date(days_back=730)
    })

print(f"\n✅ Doctors done in {time.time() - start_time_docs:.1f}s")
print(f"🗺️  Mapped {len(CITY_ZIP_MAP)} cities to ZIP codes.")

# B. ADMINS
print("\n🔐 Generating Admins...")
for _ in range(NUM_ADMINS):
    fn = fake.first_name()
    ln = fake.last_name()
    admins.append({
        "email": generate_unique_email(fn, ln, role="admin"),
        "password": HASH_ADMIN,
        "telephone": fake.phone_number(),
        "createdAt": get_random_creation_date(days_back=100)
    })

# C. PATIENTS & APPOINTMENTS
print(f"\n🤒 Generating {NUM_PATIENTS} Patients & Appointments (Questo richiederà tempo)...")
start_time_patients = time.time()

cities_list = list(CITY_ZIP_MAP.keys())
if not cities_list: cities_list = ["New York"]

log_step = 5000 if NUM_PATIENTS >= 100000 else 100

for i in range(NUM_PATIENTS):
    # Log progressivo
    if (i + 1) % log_step == 0:
        elapsed = time.time() - start_time_patients
        perc = ((i + 1) / NUM_PATIENTS) * 100
        print(f"   -> [{(i + 1)//1000}k/{NUM_PATIENTS//1000}k] Patients ({perc:.1f}%) | "
              f"Appts: {len(appointments_coll)} | Reports: {len(symptom_reports_coll)} | "
              f"Time: {elapsed:.0f}s", end='\r')

    p_id = generate_mongo_object_id()
    p_fn, p_ln = fake.first_name(), fake.last_name()
    p_tel = fake.phone_number()
    p_gender = get_weighted_gender()
    p_age = random.randint(18, 90)
    p_email = generate_unique_email(p_fn, p_ln, role="patient")
    p_created_at = get_random_creation_date(days_back=365)

    p_city = random.choice(cities_list)
    p_zip = CITY_ZIP_MAP.get(p_city, "10001")
    p_state = CITY_STATE_MAP.get(p_city, "NY")

    p_location_obj = {
        "address": fake.street_address(),
        "city": p_city,
        "state": p_state,
        "zipCode": p_zip
    }

    p_reports_embedded = []
    p_appts_embedded = []
    p_ratings_list = []

    # 1. Symptom Reports
    for r in range(random.randint(1, 3)):
        sr_date = fake.date_time_between(start_date='-60d', end_date='now')
        current_symptoms = random.sample(list_symptoms, k=random.randint(1, 3))
        current_diagnoses = random.sample(list_diseases, k=2)
        context_str = generate_medical_context(current_symptoms)

        report = {
            "patientId": p_id,
            "context": context_str,
            "symptoms": current_symptoms,
            "possibleDiagnosies": current_diagnoses,
            "patientAge": p_age,
            "patientGender": p_gender,
            "patientLocation": p_city,
            "createdAt": sr_date
        }
        symptom_reports_coll.append(report)

        p_reports_embedded.append({
            "context": context_str,
            "symptoms": current_symptoms,
            "possibleDiagnosies": current_diagnoses,
            "createdAt": sr_date
        })

    # 2. Appointments (AUMENTATI: 3-10 per paziente)
    # 2.1 Start Date spostata indietro (-180d) per avere più storico e più voti
    for _ in range(random.randint(3, 10)):
        doc = random.choice(doctors_coll)
        # Più probabilità di date nel passato (-180gg a +60gg)
        apt_date = fake.date_time_between(start_date='-180d', end_date='+60d').replace(minute=0, second=0, microsecond=0)

        is_past = apt_date < datetime.now()

        # --- Gestione CANCELLED vs COMPLETED nel passato ---
        if is_past:
            status = random.choices(["COMPLETED", "CANCELLED"], weights=[85, 15], k=1)[0]
        else:
            status = random.choice(["SCHEDULED", "PENDING", "CONFIRMED"])

        # --- RATING LOGIC (MOLTI PIÙ RATING) ---
        rating_val = 0
        if is_past and status == "COMPLETED":
            # 90% di probabilità di lasciare un rating se completato
            if random.random() < 0.90:
                # Distribuzione realistica (pochi 1-2, molti 4-5)
                rating_val = random.choices([1, 2, 3, 4, 5], weights=[5, 5, 15, 35, 40], k=1)[0]

        appt_id = generate_mongo_object_id()
        appt_created_at = apt_date - timedelta(days=random.randint(1, 10))

        # --- APPOINTMENT MASTER ---
        appt = {
            "_id": appt_id,
            "dateTime": apt_date,
            "location": doc["location"],
            "status": status,
            "patientId": p_id,
            "patientFirstName": p_fn,
            "patientLastName": p_ln,
            "patientTelephone": p_tel,
            "patientEmail": p_email,
            "patientAge": p_age,
            "patientGender": p_gender,
            "doctorId": doc["_id"],
            "specialties": doc["specialties"],
            "doctorRating": float(rating_val),
            "createdAt": appt_created_at
        }
        appointments_coll.append(appt)

        # Aggiornamento Dottore
        doc["totalAppointments"] += 1

        if rating_val > 0:
            doc["ratings"].append(rating_val)
            doc["ratingCount"] += 1
            doc["avgRating"] = round(sum(doc["ratings"]) / doc["ratingCount"], 1)

            rating_obj = {
                "doctorNpi": doc["npi"],
                "doctorFirstName": doc["firstName"],
                "doctorLastName": doc["lastName"],
                "rating": rating_val
            }
            p_ratings_list.append(rating_obj)

        # --- DOCTOR LISTS LOGIC (Solo Futuri/Settimana Corrente) ---
        if not is_past and status in ["SCHEDULED", "CONFIRMED"] and is_in_current_week(apt_date):
            doc["bookedThisWeek"].append({
                "appointmentId": appt_id,
                "dateTime": apt_date,
                "location": doc["location"],
                "status": status,
                "patientFirstName": p_fn,
                "patientLastName": p_ln,
                "patientTelephone": p_tel
            })

        elif not is_past and status in ["SCHEDULED", "CONFIRMED"] and apt_date > (datetime.now() + timedelta(days=7)):
            doc["futureAppointments"].append(str(appt_id))

        # --- PATIENT EMBEDDED ---
        p_appts_embedded.append({
            "appointmentId": appt_id,
            "dateTime": apt_date,
            "location": doc["location"],
            "status": status,
            "doctorLastName": doc["lastName"],
            "doctorFirstName": doc["firstName"],
            "doctorNpi": doc["npi"],
            "doctorSpecialties": doc["specialties"],
            "doctorEmail": doc["email"],
            "doctorTelephone": doc["telephone"]
        })

    # 3. Patient
    patients.append({
        "_id": p_id,
        "email": p_email,
        "password": HASH_PATIENT,
        "telephone": p_tel,
        "firstName": p_fn,
        "lastName": p_ln,
        "gender": p_gender,
        "age": p_age,
        "location": p_location_obj,
        "ratings": p_ratings_list,
        "bookedAppointments": p_appts_embedded,
        "recentSymptomReports": p_reports_embedded,
        "createdAt": p_created_at
    })

print(f"\n✅ Patients generation DONE in {time.time() - start_time_patients:.1f}s")
print(f"   -> Total Appointments: {len(appointments_coll)}")
print(f"   -> Total Reports: {len(symptom_reports_coll)}")

# --- 3. EXPORT ---
output_files = [
    (admins, "mongo_admins.json"),
    (doctors_coll, "mongo_doctors.json"),
    (patients, "mongo_patients.json"),
    (appointments_coll, "mongo_appointments.json"),
    (symptom_reports_coll, "mongo_symptom_reports.json")
]

print("\n💾 Saving files (Questo potrebbe richiedere tempo)...")
for data, filename in output_files:
    start_save = time.time()
    print(f"   -> Writing {filename} ({len(data)} records)... ", end='', flush=True)
    full_path = os.path.join(OUTPUT_DIR, filename)
    with open(full_path, 'w', encoding='utf-8') as f:
        json.dump(data, f, default=json_serial, indent=2, ensure_ascii=False)
    print(f"Done! ({time.time() - start_save:.2f}s)")

print(f"\n🎉 TUTTO COMPLETATO! I file sono nella cartella '{OUTPUT_DIR}'")