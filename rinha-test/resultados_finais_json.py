import json
from os import walk
from os.path import join, isfile
import sys

summary = []

for (dirpath, dirnames, filenames) in walk("../participantes/"):
    for filename in filenames:
        entry = {}
        if (filename == "info.json"):
            info_file = join(dirpath, "info.json")
            with open(info_file) as f:
                try:
                    entry.update({
                        "info": json.loads(f.read())
                    })
                except Exception as ex:
                    entry.update({
                        "info": None
                    })

            final_result_file = join(dirpath, "final-results.json")
            errors_log_file = join(dirpath, "error.logs")
            entry.update({"erro_na_execucao": isfile(errors_log_file)})

            if (isfile(final_result_file)):
                with open(final_result_file) as f:
                    final_results = f.read()
                    if (final_results):
                        entry.update({
                            "resultado_final": json.loads(final_results)
                        })
            else:
                entry.update({
                    "resultado_final": None
                })

        if (entry):
            summary.append(entry)

summary_file = sys.argv[1] if len(sys.argv) > 1 else "../resultados-finais+participantes-info.json"

with open(summary_file, 'w') as pf:
    pf.write(json.dumps(summary))
