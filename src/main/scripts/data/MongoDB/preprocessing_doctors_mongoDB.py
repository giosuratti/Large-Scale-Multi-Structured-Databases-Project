import pandas as pd

# Percorso del file CSV originale NPPES/NPI
input_file = "NPI.csv"  # sostituire con il tuo file

# Percorso del file CSV di output
output_file = "dottori_processati.csv"

# Leggi il CSV (usa low_memory=False per file grandi)
df = pd.read_csv(input_file, dtype=str, low_memory=False)

# Se alcune colonne non esistono, segnala e crea vuote
columns_needed = [
    'NPI', 'Provider Last Name', 'Provider First Name', 'gndr',
    'pri_spec', 'sec_spec_1', 'adr_ln_1', 'City/Town', 'State', 'ZIP Code', 'Telephone Number'
]

for col in columns_needed:
    if col not in df.columns:
        print(f"Colonna mancante: {col}. Verrà creata vuota.")
        df[col] = ""

# Mantieni solo le colonne desiderate
df_processed = df[columns_needed]

# Salva il CSV preprocessato
df_processed.to_csv(output_file, index=False)

print(f"Preprocessing completato. File salvato in: {output_file}")
