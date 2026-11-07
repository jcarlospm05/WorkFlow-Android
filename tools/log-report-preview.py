"""Expose two synthetic native report pages for review, never production data."""
from pathlib import Path
import base64,sys
root=Path(sys.argv[1])
for name in ['reporte-real-1-GENERAL.jpg','reporte-real-16-TICKET.jpg']:
    candidates=list(root.rglob(name))
    if not candidates: continue
    data=base64.b64encode(candidates[0].read_bytes()).decode('ascii')
    print('WORKFLOW_REPORT_BEGIN '+name)
    for start in range(0,len(data),8000):print('WORKFLOW_REPORT_CHUNK '+data[start:start+8000])
    print('WORKFLOW_REPORT_END')
