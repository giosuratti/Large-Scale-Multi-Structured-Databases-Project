import pandas as pd
import sys
# numpy non è più necessario poiché non usiamo np.array_split

# --- CONFIGURAZIONE ---

INPUT_FILE = "doctors_unique.csv"
OUTPUT_FILE = "dottori_output_neo4j.csv" # Nuovo nome per il file di output singolo

# Colonne di specializzazione da cercare (in ordine di priorità)
SPEC_COLS_PRIORITY = ["pri_spec", "sec_spec_1"]

# --- FUNZIONI DI ELABORAZIONE ---

def load_and_clean_data(input_file):
    """Carica il file CSV, pulisce i valori mancanti e identifica la colonna di specializzazione primaria."""
    try:
        df = pd.read_csv(input_file, dtype=str)
    except FileNotFoundError:
        print(f"Errore: File non trovato: {input_file}")
        sys.exit(1)
        
    df = df.fillna("")

    # Identifica la colonna di specializzazione primaria presente nel DataFrame
    primary_spec_col = next((col for col in SPEC_COLS_PRIORITY if col in df.columns), None)

    if not primary_spec_col:
        print(
            f"Errore: Nessuna delle colonne di specializzazione ({', '.join(SPEC_COLS_PRIORITY)}) è stata trovata nel file."
        )
        sys.exit(1)

    print(f"Verrà mantenuta solo la specializzazione dalla colonna: {primary_spec_col}")
    
    return df, primary_spec_col

def process_doctors(df, primary_spec_col):
    """
    Raggruppa i dati, seleziona la specializzazione primaria, formatta il testo
    e restituisce un unico DataFrame ordinato per cognome.
    """
    
    # Dizionario per l'aggregazione (Logica per la selezione singola)
    agg_dict = {
        "Provider First Name": "first",
        "Provider Last Name": "first",
        "City/Town": "first",
        "Telephone Number": "first",
        primary_spec_col: "first", 
    }

    # 1. Raggruppamento per NPI (Identificatore Univoco del Dottore)
    df_unique = (
        df.groupby("NPI")
          .agg(agg_dict)
          .reset_index()
    )

    # 2. Rinominare colonne per l'output finale
    df_unique = df_unique.rename(columns={
        "City/Town": "City",
        "Telephone Number": "Telephone",
        primary_spec_col: "Specialization"
    })
    
    # 3. Formattazione della specializzazione (Title Case)
    df_unique["Specialization"] = df_unique["Specialization"].str.title()
    print("Specializzazioni formattate in Title Case.")

    
    # Colonne finali desiderate
    FINAL_COLUMNS = [
        "NPI",
        "Provider First Name",
        "Provider Last Name",
        "City",
        "Telephone",
        "Specialization"
    ]
    df_unique = df_unique[FINAL_COLUMNS]

    # 4. Ordinamento lessicografico per Cognome
    df_unique = df_unique.sort_values(by="Provider Last Name", ascending=True)

    # Restituisce il DataFrame unificato e ordinato
    return df_unique

# --- MAIN EXECUTION ---

def main():
    print("--- Inizio Processo di Trasformazione e Unificazione Dati Dottori ---")
    
    # 1. Caricamento e pulizia dei dati
    df, primary_spec_col = load_and_clean_data(INPUT_FILE)
    if df is None:
        return

    try:
        # 2. Processamento, Formattazione e Ordinamento
        df_final = process_doctors(df, primary_spec_col)
        
        # 3. Output finale su un unico file
        df_final.to_csv(OUTPUT_FILE, index=False)
        
        print(f"\n✅ Processo Completato con successo! Creato il file unico: {OUTPUT_FILE} ({len(df_final)} righe).")
        
    except Exception as e:
        print(f"Si è verificato un errore durante il processamento: {e}")

if __name__ == "__main__":
    main()

