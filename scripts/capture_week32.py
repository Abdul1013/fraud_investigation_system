#!/usr/bin/env python3
# ============================================================================
# FILE: scripts/capture_week32.py
# PURPOSE: Verify stored telemetry against the actual lifecycle audit and preserve sanitized evidence.
# OWNER: Platform & Operations
# SINCE: week-32
# RELATED: OperationalDemoTest, DurableWorkflowIntegrationTest
# NOTES: Only loopback monitoring APIs are read; this script never commits, deploys or exports credentials.
# ============================================================================
import argparse
import base64
import json
from pathlib import Path
import re
import shutil
import urllib.request
import urllib.parse

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--source',type=Path,required=True,help='Directory containing actual test-produced Week 32 evidence')
arguments=parser.parse_args()
output=Path('docs/evidence/week32');output.mkdir(parents=True,exist_ok=True)
for name in ['native-usage.json','alert-timeline.json','lifecycle-audit.json']:
    source=arguments.source/name
    if source.resolve()!=(output/name).resolve():shutil.copy(source,output/name)
usage=json.loads((output/'native-usage.json').read_text())['report']
alert=json.loads((output/'alert-timeline.json').read_text())['report']
audit=json.loads((output/'lifecycle-audit.json').read_text())
assert usage['usage']['reported'] and usage['usage']['inputTokens']>0
assert alert['fired'] and alert['recovered']
assert any(row['providerAlertFiring'] for row in alert['timeline'])
assert re.fullmatch('[0-9a-f]{32}',audit['traceId'])
assert all(row['trace_id']==audit['traceId'] for row in audit['audit'])

def get(url):
    request=urllib.request.Request(url,headers={'Accept':'application/json'})
    with urllib.request.urlopen(request,timeout=20) as response:
        assert response.status==200
        return json.load(response)

def save(name,purpose,value):
    (output/name).write_text(json.dumps({'_file':str(output/name),'_purpose':purpose,'report':value},indent=2)+'\n')

trace=get('http://127.0.0.1:3200/api/traces/'+audit['traceId'])
spans=[]
def visit(value):
    if isinstance(value,dict):
        if 'spanId' in value and 'name' in value:spans.append(value)
        for item in value.values():visit(item)
    elif isinstance(value,list):
        for item in value:visit(item)
visit(trace)
expected=base64.b64encode(bytes.fromhex(audit['traceId'])).decode()
assert spans and all(span['traceId'] in (expected,audit['traceId']) for span in spans)
names={span['name'] for span in spans}
required={'fraud.outbox','fraud.activity','fraud.model','fraud.tool','fraud.retrieval','fraud.approval','fraud.execution'}
assert required<=names and any(name.startswith('http post') for name in names)
serialized=json.dumps(trace)
for forbidden in ['RESTRICTED_C3_SENTINEL','test-only-password','Synthetic durable recovery','Review synthetic evidence']:
    assert forbidden not in serialized
assert not re.search(r'Bearer [A-Za-z0-9._~-]{12,}',serialized)
save('tempo-trace.json','Actual collector-sanitized Tempo spans matched to the action audit trace ID',trace)
health=get('http://127.0.0.1:3000/api/health')
dashboard=get('http://127.0.0.1:3000/api/dashboards/uid/fraud-week32')['dashboard']
assert health['database']=='ok' and dashboard['uid']=='fraud-week32'
targets=get('http://127.0.0.1:9090/api/v1/targets')['data']['activeTargets']
assert targets and all(target['health']=='up' for target in targets)
rules=get('http://127.0.0.1:9090/api/v1/rules')['data']['groups']
rule_names=sorted(rule['name'] for group in rules for rule in group['rules'])
assert {'FraudProviderErrors','FraudOutboxBacklog','FraudOperationLatency','FraudExecutionErrors'}<=set(rule_names)
save('operational-checks.json','Actual monitoring readiness, dashboard provisioning and trace completeness checks',{
    'grafanaVersion':health['version'],'dashboardUid':dashboard['uid'],'panelCount':len(dashboard['panels']),
    'prometheusTargetsUp':len(targets),'alertRules':rule_names,'traceId':audit['traceId'],'spanCount':len(spans),
    'requiredStages':sorted(required),'auditEvents':len(audit['audit']),'nativeUsageReported':True,
    'providerAlertFired':True,'providerAlertRecovered':True,'longWindowSlo':'not demonstrated'})
query='histogram_quantile(0.95, sum by (le) (increase(fraud_operation_duration_milliseconds_bucket{stage="provider",outcome="error"}[30m]))) / 1000'
measured=get('http://127.0.0.1:9090/api/v1/query?'+urllib.parse.urlencode({'query':query}))['data']['result']
assert measured and float(measured[0]['value'][1])>1
save('slow-operation.json','Actual failed-provider fixture latency; short-window demonstration, not a production workload benchmark',{
    'exportedTimerUnit':'milliseconds','reportedUnit':'seconds','window':'30m demonstration history',
    'p95Seconds':float(measured[0]['value'][1]),'scenario':'HTTP fixture with 1.2-second delay and 503 status'})
print('Captured and validated native usage, alert recovery, correlated trace/audit, dashboard and live monitoring targets.')
