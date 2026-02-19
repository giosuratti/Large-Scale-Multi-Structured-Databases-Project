import pandas as pd
import numpy as np

def process_medical_data(input_file):
    # Carica il dataset
    # Assumiamo che la colonna 0 sia la malattia e le altre i sintomi
    df = pd.read_csv(input_file)

    # Pulizia nomi colonne (rimuove spazi extra nei nomi dei sintomi)
    df.columns = df.columns.str.strip()

    # La prima colonna è la malattia, le salviamo il nome
    disease_col_name = df.columns[0]

    # 1. Calcolo degli attributi per i Nodi DISEASE (Prior Probability)
    total_records = len(df)
    disease_counts = df[disease_col_name].value_counts().reset_index()
    disease_counts.columns = ['name', 'count']

    # Calcolo la probabilità a priori (Prior)
    disease_counts['prior'] = disease_counts['count'] / total_records

    # Salva il CSV per i nodi Disease
    disease_counts.to_csv('diseases_import.csv', index=False)
    print(f"Generato diseases_import.csv con {len(disease_counts)} malattie.")

    # 2. Calcolo degli attributi per le RELAZIONI (Likelihood)
    # Raggruppa per malattia e somma i valori (che sono 0 o 1) per contare le occorrenze dei sintomi
    symptom_counts = df.groupby(disease_col_name).sum()

    relationships = []

    for disease in symptom_counts.index:
        # Recupera il numero totale di casi per questa specifica malattia
        n_disease_cases = disease_counts[disease_counts['name'] == disease]['count'].values[0]

        # Estrai la riga dei conteggi per questa malattia
        row = symptom_counts.loc[disease]

        for symptom, count in row.items():
            if count > 0: # Creiamo la relazione solo se il sintomo esiste almeno una volta
                # Probabilità condizionata P(Sintomo | Malattia)
                prob = count / n_disease_cases

                relationships.append({
                    'disease_name': disease,
                    'symptom_name': symptom,
                    'weight': count,      # Numero assoluto di volte
                    'probability': prob   # Probabilità (0.0 a 1.0)
                })

    rel_df = pd.DataFrame(relationships)
    rel_df.to_csv('relationships_import.csv', index=False)
    print(f"Generato relationships_import.csv con {len(rel_df)} relazioni.")

# Esempio di utilizzo
if __name__ == "__main__":
    # Sostituisci con il percorso reale del tuo file
    try:
        process_medical_data("disease_symptom_dataset.csv")
        print("\nTutto pronto! Ora puoi caricare i file su Neo4j.")
    except FileNotFoundError:
        print("Errore: File 'disease_symptom_dataset.csv' non trovato. Verifica il percorso.")