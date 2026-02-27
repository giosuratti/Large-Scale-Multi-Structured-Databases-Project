# 🩺 Find Your Doc
**Distributed and Intelligent Medical Booking Platform**

"Find Your Doc" is an advanced medical appointment booking system designed on a **large-scale multi-database architecture**. It leverages the flexibility of MongoDB for transactional data, the relational power of Neo4j for diagnostic inference, and the speed of Redis for concurrency control and caching.

Developed as the final project for the *Large-Scale and Multi-Structured Databases* course (Computer Engineering and Artificial Intelligence & Data Engineering) at the University of Pisa.

---

## 🏗️ Architecture and Technologies

The system is built upon three main storage pillars orchestrated by a Spring Boot backend:

* ☕ **Backend**: Java 17, Spring Boot, Spring Data, Spring Cache, Spring Security (JWT).
* 🍃 **MongoDB (Document Database)**: Acts as the *Source of Truth*. Manages user profiles (Patients, Doctors, Admins), appointments, symptom reports, and ratings. It utilizes advanced architectural patterns (such as balanced embedding and Partial Indexes) to heavily optimize reads and writes.
* 🌐 **Neo4j (Graph Database)**: Manages an extensive medical ontology. It connects **Symptoms** to **Diseases**, diseases to **Specializations**, and specializations to **Doctors**. It is the engine behind the intelligent pre-diagnosis symptom checker.
* ⚡ **Redis (In-Memory Data Store)**: Performs two critical tasks:
    1.  **Distributed Lock**: Prevents race conditions (double-booking) during simultaneous bookings of the same slot using distributed locks.
    2.  **Caching**: Stores frequent diagnoses, doctor availabilities, and search queries to slash latency (ensuring sub-millisecond access times).
* 🐍 **Scripting & Data Ingestion**: Python scripts for preprocessing, cleaning, and ingesting real-world datasets (Kaggle for diseases, CMS Provider Data for doctors) as well as generating synthetic data.

---

## ✨ Key Features

### 🧑‍⚕️ For Patients
* **Smart Search Engine**: Find doctors by city, name, or specialization.
* **Symptom Checker (Graph Inference)**: Input your symptoms and let the Neo4j engine suggest possible diseases and direct you to the most suitable specialist.
* **Secure Booking**: Book appointments in real-time with zero risk of overlaps, thanks to Redis concurrency management.
* **Personal Dashboard**: Manage your visit history, cancel appointments, view clinical reports, and leave reviews.

### ⚕️ For Doctors
* **Agenda Management**: Flexible insertion of availability slots.
* **Professional Dashboard**: Immediate view of the current week's appointments (loaded instantly via optimized MongoDB indexes) and future schedule.
* **Profile Management**: Update contact information and clinic location.

### 🛠️ For Administrators
* **Advanced Analytics**: Monitoring dashboard to track system metrics (most active doctors, diagnosis trends, appointment cancellation rates).
* **Automated Maintenance**: Handling of synchronization *Cron Jobs* between historical archives and doctors' active agendas.

---

## 🗄️ Data Structure

### The Graph (Neo4j)
The graph database solves the indirect recommendation problem. The standard inference path is:
`(:Symptom) -[:INDICATES]-> (:Disease) -[:REQUIRES]-> (:Specialization) <-[:HAS_SPECIALIZATION]- (:Doctor)`

### The Documents (MongoDB)
Data modeling follows a *Query-Driven* logic. For instance, current week appointments do not require complex joins or scans: they are directly embedded within the Doctor document, ensuring instant load times when the professional opens the app.

---

## 🚀 Requirements and Installation

### Prerequisites
* **Java 17+** and **Maven**
* **Docker** and **Docker Compose** (recommended for running local database instances)
* **Python 3.10+** (only for data population scripts)

### 1. Database Setup
Start your instances of MongoDB, Neo4j, and Redis. Update the `src/main/resources/application.properties` file with the correct credentials and URIs of your local or cloud databases (e.g., MongoDB Atlas, Neo4j Aura).

### 2. Data Population (Optional but recommended)
In the `src/main/scripts` directory, you will find the Python scripts required to generate and populate the system data.
* Check the instructions inside the Python files (`populate_mongoDB.py`, `preprocessing.py`) to generate graphs and collections in batch.

### 3. Application Startup
Navigate to the project root directory and run:
```bash
mvn clean install
mvn spring-boot:run
```
The backend will be available at http://localhost:8080/swagger-ui/index.html.
---

## 👥 Authors
* **Elia Marabotto**
* **Antonio Querci**
* **Giosuè Ratti**

Developed for the 2025/2026 Academic Year - University of Pisa.
