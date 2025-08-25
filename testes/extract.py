import os
import json
import csv
import re

# Caminho raiz para iniciar a busca
ROOT_DIR = "."

# Nome do arquivo de saída
OUTPUT_FILE = "resultados-final.csv"

# Campos que queremos extrair
FIELDS = [
    "total_liquido",
    "p99.valor",
    "multa.porcentagem",
    "lag.lag",
    "pa gamentos_solicitados.qtd_sucesso",
    "pagamentos_solicitados.qtd_falha",
    "multa.total",
]

def get_nested(data, path):
    """Extrai valor aninhado de um dict usando caminho com '.'"""
    keys = path.split(".")
    for key in keys:
        if isinstance(data, dict) and key in data:
            data = data[key]
        else:
            return None
    return data

def preprocess_p99(value):
    """Remove 'ms' e converte para número"""
    if value is None:
        return None
    if isinstance(value, str):
        value = value.strip().lower().replace("ms", "").strip()
    return convert_to_numeric(value)

def convert_to_numeric(value):
    """Tenta converter para float/int, retorna None se não conseguir"""
    if value is None:
        return None
    try:
        f = float(value)
        return int(f) if f.is_integer() else f
    except (ValueError, TypeError):
        return None

def format_number(value):
    """Converte número para string com vírgula como separador decimal"""
    if value is None:
        return ""
    if isinstance(value, (int, float)):
        return str(value).replace(".", ",")
    return str(value)

def extract_nos_from_path(filepath):
    """Extrai o primeiro número do nome do diretório"""
    dir_name = os.path.basename(os.path.dirname(filepath))
    match = re.search(r"\d+", dir_name)
    return int(match.group()) if match else None

def extract_workers_from_filename(filepath):
    """Extrai o primeiro número do nome do arquivo"""
    file_name = os.path.basename(filepath)
    match = re.search(r"\d+", file_name)
    return int(match.group()) if match else None

def main():
    resultados = []

    for root, _, files in os.walk(ROOT_DIR):
        for file in files:
            if file.endswith("-final-results.json"):
                filepath = os.path.join(root, file)
                try:
                    with open(filepath, "r", encoding="utf-8") as f:
                        data = json.load(f)
                    
                    row = {
                        "arquivo": filepath,
                        "nos": extract_nos_from_path(filepath),
                        "workers": extract_workers_from_filename(filepath)
                    }
                    
                    for field in FIELDS:
                        value = get_nested(data, field)
                        if field == "p99.valor":
                            value = preprocess_p99(value)
                        row[field] = format_number(value)
                    
                    resultados.append(row)
                except Exception as e:
                    print(f"Erro ao processar {filepath}: {e}")

    # Grava CSV separado por ponto e vírgula
    with open(OUTPUT_FILE, "w", newline="", encoding="utf-8") as csvfile:
        writer = csv.DictWriter(csvfile, fieldnames=["arquivo", "nos", "workers"] + FIELDS, delimiter=";")
        writer.writeheader()
        writer.writerows(resultados)

    print(f"Processo concluído. Resultados em {OUTPUT_FILE}")

if __name__ == "__main__":
    main()
