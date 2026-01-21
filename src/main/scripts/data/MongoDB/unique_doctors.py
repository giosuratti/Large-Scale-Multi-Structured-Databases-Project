import pandas as pd

# Percorso del CSV preprocessato
input_file = "dottori_processati.csv"
output_file = "doctors_unique.csv"

# Leggi il CSV
df = pd.read_csv(input_file, dtype=str)

# Rimuove righe duplicate
# Puoi scegliere le colonne su cui basarti per l'unicità, ad esempio NPI
df_unique = df.drop_duplicates(subset=['NPI'])

# Salva il dataset filtrato
df_unique.to_csv(output_file, index=False)

print(f"Dataset univoco salvato in: {output_file}")
print(f"Righe originali: {len(df)}, righe uniche: {len(df_unique)}")
